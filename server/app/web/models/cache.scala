package web.models

import com.gigahex.commons.models.JobType.JobType
import com.gigahex.commons.models.RunStatus.RunStatus

object CacheKeys {
  val jobIdPrefix = "jobId"
}
case class JobRunSnip(runId: Long, status: RunStatus, startedTimestamp: Long)
case class JobValue(jobId: Long,  jobType: JobType, name: String, runs: Seq[JobRunSnip])
case class MemberValue(memberId: Long, email: String, orgIds: Seq[Long])
