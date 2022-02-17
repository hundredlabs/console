package web.repo

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.searches.{SearchRequest, SearchResponse}
import com.sksamuel.elastic4s.requests.searches.queries.{BoolQuery, RangeQuery}
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties, ElasticsearchClientUri, Hit, HitReader, HttpClient, RequestFailure, Response}
import web.models.{LogSearchRequest, LogsSearchResponse}
import web.models.spark.SparkLog

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait LogsIndex {

  val TIMESTAMP      = "timestamp"
  val OFFSET         = "offset"
  val LOG_LEVEL      = "level"
  val MESSAGE        = "message"
  val THREAD         = "thread"
  val CLASS_NAME     = "classname"
  val REMOTE_ADDR    = "remote_address"
  val JOB_RUN_ID     = "job_run_id"
  val AGG_LOG_LEVEL  = "agg_level"
  val AGG_CLASSNAME  = "agg_classname"
  val AGG_START_DATE = "agg_start_date"
  val AGG_END_DATE   = "agg_end_date"

  def setupIndex(orgId: Long, jobId: Long)(implicit ec: ExecutionContext): Future[Boolean]

  def addLogs(jobId: Long, runId: Long, remoteHost: String, lines: Seq[SparkLog])(implicit ec: ExecutionContext): Future[Int]

  def getLogs(jobId: Long, runId: Long, tz: String, lastTs: Long)(implicit ec: ExecutionContext): Future[LogsSearchResponse]

  def searchLogs(searchRequest: LogSearchRequest, jobId: Long, runId: Long)(implicit ec: ExecutionContext): Future[LogsSearchResponse]

  def deleteLog(jobId: Long)(implicit ec: ExecutionContext): Future[Boolean]

}

class ESLogIndex(client: ElasticClient) extends LogsIndex {

  implicit object SparkLogHitReader extends HitReader[SparkLog] {

    override def read(hit: Hit): Try[SparkLog] = Try(
      SparkLog(
        hit.sourceAsMap(OFFSET).toString.toLong,
        hit.sourceAsMap(TIMESTAMP).toString.toLong,
        hit.sourceAsMap(CLASS_NAME).toString,
        hit.sourceAsMap(THREAD).toString,
        hit.sourceAsMap(MESSAGE).toString,
        hit.sourceAsMap(LOG_LEVEL).toString
      )
    )
  }

  import com.sksamuel.elastic4s.ElasticDsl._
  private val indexPrefix = "logs_index_"

  override def setupIndex(orgId: Long, jobId: Long)(implicit ec: ExecutionContext): Future[Boolean] = {
    client
      .execute {
        createIndex(s"$indexPrefix${jobId}").mapping(
          properties(
            longField(OFFSET),
            longField(TIMESTAMP),
            keywordField(LOG_LEVEL),
            textField(MESSAGE).fielddata(true),
            keywordField(THREAD),
            keywordField(CLASS_NAME),
            keywordField(JOB_RUN_ID)
          )
        )
      }
      .map(f =>
        f.fold(
          err => {

            false
          },
          response => {

            response.acknowledged
          }
      ))
  }

  override def deleteLog(jobId: Long)(implicit ec: ExecutionContext): Future[Boolean] =
    client
      .execute(deleteIndex(s"${indexPrefix}${jobId}"))
      .map { r =>
        r.result.acknowledged
      }

  override def addLogs(jobId: Long, runId: Long, remoteHost: String, lines: Seq[SparkLog])(
      implicit ec: ExecutionContext): Future[Int] = {

    val indexRequests = lines.map(
      l =>
        indexInto(s"$indexPrefix${jobId}").fields(
          Map(
            OFFSET     -> l.offset,
            TIMESTAMP  -> l.timestamp,
            LOG_LEVEL  -> l.level.trim,
            THREAD     -> l.thread.trim,
            CLASS_NAME -> l.classname.trim,
            MESSAGE    -> l.message.trim,
            JOB_RUN_ID -> runId.toString
          )
      ))
    client
      .execute(bulk(indexRequests).refresh(RefreshPolicy.IMMEDIATE))
      .map(r =>
        r.fold(rf => {
          println(rf.error.reason)
          0
        }, onSuccess => onSuccess.successes.size))
  }

  private def buildFetchRequest(jobId: Long, runId: Long, lastTs: Long): SearchRequest = {
    search(s"$indexPrefix${jobId}")
      .termQuery(JOB_RUN_ID, runId.toString)
      .limit(20)
      .searchAfter(Seq(lastTs))
      .sortByFieldAsc(OFFSET)
      .aggregations(
        termsAgg(AGG_LOG_LEVEL, LOG_LEVEL),
        termsAgg(AGG_CLASSNAME, CLASS_NAME).copy(size = Some(15)),
        maxAgg(AGG_END_DATE, TIMESTAMP),
        minAgg(AGG_START_DATE, TIMESTAMP)
      )
  }

  private def handleFetchResponse(r: Response[SearchResponse], tz: String, reqLastOffset: Long): LogsSearchResponse = {

    val levels     = r.result.aggregations.terms(AGG_LOG_LEVEL).buckets.map(_.key)
    val classnames = r.result.aggregations.terms(AGG_CLASSNAME).buckets.map(_.key)
    val endDt = r.result.aggregations
      .max(AGG_END_DATE)
      .value
      .map(ts => {
        val df = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss").withZone(ZoneId.of(tz))
        df.format(Instant.ofEpochMilli(ts.toLong))
      })

    val startDt = r.result.aggregations
      .min(AGG_START_DATE)
      .value
      .map(ts => {
        val df = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss").withZone(ZoneId.of(tz))
        df.format(Instant.ofEpochMilli(ts.toLong))
      })
    val logLines   = r.result.to[SparkLog]
    val lastOffset = if (logLines.size > 0) logLines.map(_.offset).max else reqLastOffset
    LogsSearchResponse(
      timeTaken = r.result.took,
      resultSize = r.result.hits.total.value,
      logs = r.result.to[SparkLog],
      lastOffset = lastOffset,
      levels = levels,
      classnames = classnames,
      startDate = startDt,
      endDate = endDt
    )
  }

  override def getLogs(jobId: Long, taskId: Long, tz: String, lastOffset: Long)(implicit ec: ExecutionContext): Future[LogsSearchResponse] = {
    client
      .execute {
        buildFetchRequest(jobId, taskId, lastOffset)
      }
      .map(r => handleFetchResponse(r, tz, lastOffset))
  }

  private def buildQuery(searchRequest: LogSearchRequest, jobId: Long, runId: Long): BoolQuery = {
    val valuedFilters = searchRequest.filters.filter(_._2.size > 0)
    (searchRequest.startTime, searchRequest.endTime, valuedFilters) match {
      case (Some(st), Some(end), filters) if filters.size > 0 =>
        val esFilters = filters.map {
          case (field, value) => termsQuery(field, value.toSeq)
        }
        val rangeQuery = RangeQuery(TIMESTAMP).gte(st).lte(end)
        BoolQuery().filter(esFilters ++ Seq(rangeQuery))

      case (_, _, filters) if filters.size > 0 =>
        val esFilters = filters.map {
          case (field, value) => termsQuery(field, value.toSeq)
        }
        BoolQuery().filter(esFilters)

      case (Some(st), Some(end), filters) if filters.size == 0 =>
        val rangeQuery = RangeQuery(TIMESTAMP).gte(st).lte(end)
        BoolQuery().filter(Seq(rangeQuery))

      case _ => BoolQuery()
    }
  }
  override def searchLogs(searchRequest: LogSearchRequest, jobId: Long, runId: Long)(implicit ec: ExecutionContext): Future[LogsSearchResponse] = {

    val q = buildQuery(searchRequest, jobId, runId)
    client
      .execute {
        search(s"$indexPrefix${jobId}")
          .bool {
            q.must(termQuery(JOB_RUN_ID, runId))
              .should(matchQuery(MESSAGE, searchRequest.query), wildcardQuery(MESSAGE, searchRequest.query))
              .minimumShouldMatch(1)
          }
          .searchAfter(Seq(searchRequest.lastOffset))
          .limit(20)
          .sortByFieldAsc(OFFSET)
      }
      .map(r => {
        val logLines   = r.result.to[SparkLog]
        val lastOffset = if (logLines.size > 0) logLines.map(_.offset).max else searchRequest.lastOffset
        LogsSearchResponse(r.result.took, r.result.hits.total.value, logLines, lastOffset)
      })

  }

}
