import WebService from "./WebService";
import { IErrorHandler, IErrorType } from "./IErrorHander";
import { Status } from "./Workspace";
import { IAppCluster } from "../pages/workspace/WorkspaceDashboard/WorkspaceDashboard";

export type IWorker = {
  id: string;
  cpuCores: number;
  physicalMemory: number;
  executorsMemory: Array<{ executorId: string; data: Array<{ ts: number; memoryUsed: number }> }>;
};

export type IExecutorsStats = {
  workersCount: number;
  executorsCount: number;
  peakHeapMemoryOfExecutorUsed: number;
  allocatedHeapMemoryOfExecutor: number;
  storageMemoryUsed: number;
  storageMemoryAllocated: number;
  workers: string[];
};

export interface ItimeGraph {
  name: string;
  data: Array<{ x: string; y: string }>;
}

export interface TimeserieState {
  seriesData: Array<{ name: string; data: Array<{ x: number; y: number }> }>;
  name: string;
  message: string;
  loading: boolean;
  minValue: number;
  stacked: boolean;
  showLegend: boolean;
  yFormatter: "number" | "percentage" | "size";
}
export interface RuntimeCounters {
  executors: number;
  jobs: number;
  stages: number;
  tasks: number;
}
export interface CompareRun {
  jobId: number;
  runId: number;
  runtime: string;
  runSeq: number;
  name: string;
  status: Status;
  avgMemoryUsed: number;
  avgCPUUsed: number;
  counters: {
    jobs: number;
    stages: number;
  };
  slowest: {
    jobId: number;
    jobRuntime: string;
    stageId: number;
    stageRuntime: string;
  };
  executors: {
    count: number;
    usedStorageMem: number;
    maxStorageMem: number;
    totalTasks: number;
    inputRead: number;
  };
  config: {
    sparkVer: string;
    master: string;
    executorMem: string;
    driverMem: string;
    executorCores: number;
    memoryOverHead: string;
  };
}

export type AggMetrics = {
  appAttemptId: string;
  timeTaken: string;
  avgTaskRuntime: string;
  maxTaskRuntime: string;
  totalExecutorRuntime: string;
  totalGCTime: string;
  status: Status;
  startTime: number;
  endTime: number;
};
export type SparkAppInfo = {
  name: string;
  runId: number;
  runSeqId: number;
  appId: string;
  status: Status;
  appAttempts: Array<AggMetrics>;
};
export type students = {
  names: Array<string>;
};
export type InternalServerError = {
  path: string;
  message: string;
};
export type IllegalParam = {
  path: string;
  memberId: number;
  message: string;
};
export type BadRequest = {
  error: string;
};
export type UnAuthorized = {
  requestedResource: string;
  message: string;
};
export type JobState = {
  jobId: number;
  startedAt: string;
  name: string;
  runtime: string;
  currentStatus: Status;
  numStages: number;
  completedStages: number;
  startTime: number;
  endTime: number;
};
export type StageState = {
  stageId: number;
  stageAttemptId: number;
  name: string;
  startedAt: string;
  runtime: string;
  currentStatus: Status;
  numTasks: number;
  completedTasks: number;
  recordsRead: number;
  recordsWritten: number;
  shuffleRecordsRead: number;
  shuffleRecordsWritten: number;
  shuffleWriteSize: string;
  shuffleInputSize: string;
  inputSize: string;
  outputSize: string;
};
export interface RuntimeObservation {
  title: string;
  description: string;
  severityLevel: number;
}
export type ExecutorMetricInstance = {
  cores: number;
  status: string;
  execId: string;
  rddBlocks: number;
  maxStorageMemory: string;
  usedMemory: string;
  diskUsed: string;
  activeTasks: number;
  failedTasks: number;
  completedTasks: number;
  taskRuntime: string;
  inputSize: string;
  shuffleRead: string;
  shuffleWrite: string;
};
export type ExecutorMetrics = {
  totalCores: number;
  totalActiveTasks: number;
  totalFailedTasks: number;
  pctMemoryUsed: number;
  totalCompletedTasks: number;
  totalTaskTime: string;
  totalMemoryStorage: string;
  totalMemoryUsed: string;
  totalDiskUsed: string;
  execMetrics: Array<ExecutorMetricInstance>;
};
export type LogItem = {
  offset: number;
  timestamp: string;
  classname: string;
  thread: string;
  message: string;
  level: string;
  readableTime: string;
};
export type LogSearchResult = {
  timeTaken: number;
  startDate?: string;
  endDate?: string;
  lastOffset: number;
  resultSize: number;
  logs: Array<LogItem>;
  levels: Array<string>;
  classnames: Array<string>;
};
export type ClsHeapUsage = {
  init: number;
  used: number;
  max: number;
  fracUsage: number;
  edenSpaceUsed: number;
  oldGenUsed: number;
  survivorSpaceUsed: number;
};
export type NonHeapUsage = {
  used: number;
  max: number;
  fracUsage: number;
  init: number;
  codeCacheUsed: number;
  compressedClassSpaceUsed: number;
  metaspaceUsed: number;
};
export type JVMOverallUsage = {
  swapSize: number;
  swapFree: number;
  freeMemory: number;
  totalUsed: number;
};

export type GctTime = {
  marksweepCount: number;
  marksweepTime: number;
  scavengeCount: number;
  scavengeTime: number;
};

export type Cpu = {
  processUsage: number;
  systemUsage: number;
};

export type Metrics = {
  timestamp: number;
  timeOffset: number;
  heapUsage: ClsHeapUsage;
  nonHeapUsage: NonHeapUsage;
  gcTime: GctTime;
  cpu: Cpu;
  jvmOverall: JVMOverallUsage;
};

export type DetailRuntimeMetrics = {
  executorId: string;
  status: string;
  address: string;
  jvmMemSize: number;
  jvmMemAllocatedSize: number;
  cpusAvailable: number;
  cpuAllocated: number;
  metricTimeUnit: string;
  metrics: Array<Metrics>;
};

export interface SparkInstantMetric {
  offset: number;
  timestamp: number;
  metric: number;
}

export interface SparkCoresUsage {
  startTs: number;
  endTs: number;
  cores: Array<SparkInstantMetric>;
  tasks: Array<SparkInstantMetric>;
}

export interface SparkCPUUsage {
  startTs: number;
  endTs: number;
  executorsCpuUsage: Array<SparkInstantMetric>;
  systemsCpuUsage: Array<SparkInstantMetric>;
}

export interface SparkMemoryUsage {
  startTs: number;
  endTs: number;
  clusterMaxMem: Array<SparkInstantMetric>;
  clusterMemUsage: Array<SparkInstantMetric>;
  heap: Array<SparkInstantMetric>;
  offHeap: Array<SparkInstantMetric>;
}

export interface SparkHeapMemoryUsage {
  startTs: number;
  endTs: number;
  edenSpace: Array<SparkInstantMetric>;
  oldGenSpace: Array<SparkInstantMetric>;
  survivorSpace: Array<SparkInstantMetric>;
}

export interface SparkOffHeapMemoryUsage {
  startTs: number;
  endTs: number;
  metaSpace: Array<SparkInstantMetric>;
  codeCache: Array<SparkInstantMetric>;
  compressedClass: Array<SparkInstantMetric>;
}

export interface SparkOverviewMetric {
  name: string;
  appStatus: Status;
  sparkUser: string;
  appId: string;
  elapsedTime: string;
  started: string;
  config: {
    sparkVer: string;
    master: string;
    executorMem: string;
    driverMem: string;
    executorCores: number;
    memoryOverHead: string;
  };
  cpuAgg: {
    peak: number;
    avg: number;
  };
  memAgg: {
    avg: number;
    peak: number;
  };
  tasks: {
    active: number;
    completed: number;
    failed: number;
    skipped: number;
  };
}

export interface AggMetricType {
  mean: number;
  min: number;
  max: number;
  total: number;
}

export interface StageAggMetric {
  taskDuration: AggMetricType;
  gcTime: AggMetricType;
  inputReadRecords: AggMetricType;
  inputReadSize: AggMetricType;
  outputWriteRecords: AggMetricType;
  outputWriteSize: AggMetricType;
  shuffleReadRecords: AggMetricType;
  shuffleReadSize: AggMetricType;
  shuffleWriteSize: AggMetricType;
  shuffleWriteRecords: AggMetricType;
}

export interface StageMetricRow {
  name: string;
  min: number;
  mean: number;
  max: number;
  total: number;
}

interface TaskTimeline {
  schedulerDelay: number;
  executorComputeTime: number;
  shuffleReadTime: number;
  shuffleWriteTime: number;
  gettingResultTime: number;
  taskDeserializationTime: number;
  resultSerializationTime: number;
}

export interface StageSummary {
  aggMetrics: StageAggMetric;
  timeline: TaskTimeline;
}

export type RuntimeExecutorMetric = {
  executorId: string;
  maxMemory: number;
  avgUsedMemory: number;
  avgProcessCpuUsage: number;
};

export interface JobMetricList {
  totalJobs: number;
  jobs: Array<JobState>;
}

export type RuntimeExecutorMetricsResponce = Array<RuntimeExecutorMetric> | UnAuthorized | IllegalParam | IErrorType;
export type DetailRuntimeMetricsResponce = DetailRuntimeMetrics | UnAuthorized | IllegalParam | IErrorType;
export type AggMetricsResponse = SparkAppInfo | IllegalParam | UnAuthorized | IErrorType;
export type JobMetricsResponse = JobMetricList | IllegalParam | UnAuthorized | IErrorType;
export type StageMetricsResponse = Array<StageState> | IllegalParam | UnAuthorized | IErrorType;
export type ExecMetricResponse = ExecutorMetrics | UnAuthorized | IErrorType;
export type LogSearchResponse = LogSearchResult | UnAuthorized | IErrorType;
export type SparkCoresUsageResponse = SparkCoresUsage | UnAuthorized | IErrorType;
export type SparkCPUUsageResponse = SparkCPUUsage | UnAuthorized | IErrorType;
export type SparkMemoryUsageResponse = SparkMemoryUsage | UnAuthorized | IErrorType;
export type SparkHeapMemoryUsageResponse = SparkHeapMemoryUsage | UnAuthorized | IErrorType;
export type SparkOffHeapMemoryUsageResponse = SparkOffHeapMemoryUsage | UnAuthorized | IErrorType;
export type SparkOverviewMetricResponse = SparkOverviewMetric | UnAuthorized | IErrorType;
export type StageSummaryResponse = StageSummary | UnAuthorized | IErrorType;
export type RuntimeObservationResponse = Array<RuntimeObservation> | UnAuthorized | IErrorType;
export type ExecutoreStatsResponse = IExecutorsStats | UnAuthorized | IErrorType | IllegalParam;
export type AllWorkersResponse = IWorker | UnAuthorized | IErrorType | IllegalParam;
export type JobComparisonInfoResponce = CompareRun | UnAuthorized | IErrorType | IllegalParam;

export const defaultErrorTimeSeries: TimeserieState = {
  message: "Error occurred",
  name: "Metric Usage",
  stacked: true,
  seriesData: [],
  showLegend: true,
  loading: false,
  minValue: 0,
  yFormatter: "number",
};
class SparkService extends IErrorHandler {
  private webAPI: WebService = new WebService();

  /**
   *
   * @param jobId The projectId that has been registered
   * @param runId The instanceId of the run
   * @param attempt Spark application has multiple attempts
   * @param onSuccess Handler for receiving the success result
   */
  getCoresUsage = async (
    jobId: number,
    runId: number,
    attempt: string,
    startTime: number | undefined,
    endTime: number | undefined
  ): Promise<TimeserieState> => {
    try {
      const response = this.webAPI.post<SparkCoresUsageResponse>(`/web/v1/jobs/spark/overview-cores-usage`, {
        projectId: jobId,
        runId: runId,
        attemptId: attempt,
        startTime: startTime,
        endTime: endTime,
      });

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as SparkCoresUsage;
        const coresUsage = result.cores.map((v) => {
          return {
            x: v.timestamp,
            y: v.metric,
          };
        });

        const tasksUsage = result.tasks.map((v) => {
          return {
            x: v.timestamp,
            y: v.metric,
          };
        });
        return {
          message: "Success",
          name: "Task Parallelization",
          stacked: false,
          seriesData: [
            { name: "Available Cores", data: coresUsage },
            { name: "Active Tasks", data: tasksUsage },
          ],
          showLegend: true,
          loading: false,
          minValue: Math.min(coresUsage[0].x),
          yFormatter: "number",
        };
      } else {
        return {
          ...defaultErrorTimeSeries,
        };
      }
    } catch (e) {
      return {
        ...defaultErrorTimeSeries,
      };
    }
  };

  /**
   *
   * @param jobId The projectId that has been registered
   * @param runId The instanceId of the run
   * @param attempt Spark application has multiple attempts
   * @param onSuccess Handler for receiving the success result
   */
  getCPUUsage = async (
    jobId: number,
    runId: number,
    attempt: string,
    startTime: number | undefined,
    endTime: number | undefined
  ): Promise<TimeserieState> => {
    try {
      const response = this.webAPI.post<SparkCPUUsageResponse>(`/web/v1/jobs/spark/overview-cpu-usage`, {
        projectId: Number(jobId),
        runId: Number(runId),
        attemptId: attempt,
        startTime: startTime,
        endTime: endTime,
      });

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as SparkCPUUsage;
        const executorUsage = result.executorsCpuUsage.map((v) => {
          return {
            x: v.timestamp,
            y: v.metric,
          };
        });

        const systemCPU = result.systemsCpuUsage.map((v) => {
          return {
            x: v.timestamp,
            y: v.metric,
          };
        });
        return {
          message: "Success",
          name: "CPU Usage",
          stacked: false,
          seriesData: [
            { name: "System CPU", data: systemCPU },
            { name: "Executor CPU", data: executorUsage },
          ],
          showLegend: true,
          loading: false,
          minValue: Math.min(systemCPU[0].x),
          yFormatter: "percentage",
        };
      } else {
        return {
          ...defaultErrorTimeSeries,
        };
      }
    } catch (e) {
      return {
        ...defaultErrorTimeSeries,
      };
    }
  };

  /**
   * Fetch the runtime observation of the spark application
   * @param jobId
   * @param runId
   * @param attempt
   * @param onSuccess
   */
  getRuntimeObservation = async (jobId: number, runId: number, attempt: string, onSuccess: (r: Array<RuntimeObservation>) => void) => {
    try {
      const response = this.webAPI.post<RuntimeObservationResponse>(`/web/v1/jobs/spark/rutime-observations`, {
        projectId: jobId,
        runId: runId,
        attemptId: attempt,
      });

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as Array<RuntimeObservation>;
        onSuccess(result);
      } else if (r.status === 401 && r.parsedBody) {
        const body = r.parsedBody as UnAuthorized;
        this.showUnAuthorizedError(body.requestedResource);
      } else {
        const err = this.getDefaultError("Fetch Spark CPU Usage");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  /**
   * Get the memory usage time series data
   * @param jobId The projectId that has been registered
   * @param runId The instanceId of the run
   * @param attempt Spark application has multiple attempts
   * @param onSuccess Handler for receiving the success result
   */
  getNetMemoryUsage = async (
    jobId: number,
    runId: number,
    attempt: string,
    startTime: number | undefined,
    endTime: number | undefined
  ): Promise<TimeserieState> => {
    try {
      const response = this.webAPI.post<SparkMemoryUsageResponse>(`/web/v1/jobs/spark/overview-mem-usage`, {
        projectId: Number(jobId),
        runId: Number(runId),
        attemptId: attempt,
        startTime: startTime,
        endTime: endTime,
      });

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as SparkMemoryUsage;
        const heapUsage = result.heap.map((v) => {
          return {
            x: v.timestamp,
            y: v.metric,
          };
        });

        const offHeap = result.offHeap.map((v) => {
          return {
            x: v.timestamp,
            y: v.metric,
          };
        });

        return {
          message: "Success",
          name: "Memory Usage",
          stacked: true,
          seriesData: [
            { name: "Heap Memory Used", data: heapUsage },
            { name: "Off heap memory used", data: offHeap },
          ],
          showLegend: true,
          loading: false,
          minValue: Math.min(heapUsage[0].x, offHeap[0].x),
          yFormatter: "size",
        };
      } else {
        return {
          ...defaultErrorTimeSeries,
        };
      }
    } catch (e) {
      return {
        ...defaultErrorTimeSeries,
      };
    }
  };

  /**
   * Get the Heap Usage time series data
   * @param jobId The projectId that has been registered
   * @param runId The instanceId of the run
   * @param attempt Spark application has multiple attempts
   * @param onSuccess Handler for receiving the success result
   */
  getNetHeapMemoryUsage = async (
    jobId: number,
    runId: number,
    attempt: string,
    startTime: number | undefined,
    endTime: number | undefined
  ): Promise<TimeserieState> => {
    try {
      const response = this.webAPI.post<SparkHeapMemoryUsageResponse>(`/web/v1/jobs/spark/overview-heap-mem-usage`, {
        projectId: Number(jobId),
        runId: Number(runId),
        attemptId: attempt,
        startTime: startTime,
        endTime: endTime,
      });

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as SparkHeapMemoryUsage;
        const eden = result.edenSpace.map((v) => {
          return {
            x: v.timestamp,
            y: v.metric,
          };
        });

        const oldGenSpace = result.oldGenSpace.map((v) => {
          return {
            x: v.timestamp,
            y: v.metric,
          };
        });

        const survivor = result.survivorSpace.map((v) => {
          return {
            x: v.timestamp,
            y: v.metric,
          };
        });

        return {
          message: "Success",
          name: "Heap Memory",
          stacked: true,
          seriesData: [
            { name: "Eden Space", data: eden },
            { name: "Oldgen Space", data: oldGenSpace },
            { name: "Survivor Space", data: survivor },
          ],
          showLegend: true,
          loading: false,
          minValue: Math.min(eden[0].x),
          yFormatter: "size",
        };
      } else {
        return {
          ...defaultErrorTimeSeries,
        };
      }
    } catch (e) {
      return {
        ...defaultErrorTimeSeries,
      };
    }
  };

  /**
   * Get the Heap Usage time series data
   * @param jobId The projectId that has been registered
   * @param runId The instanceId of the run
   * @param attempt Spark application has multiple attempts
   * @param onSuccess Handler for receiving the success result
   */
  getNetOffHeapMemoryUsage = async (
    jobId: number,
    runId: number,
    attempt: string,
    startTime: number | undefined,
    endTime: number | undefined
  ): Promise<TimeserieState> => {
    try {
      const response = this.webAPI.post<SparkOffHeapMemoryUsageResponse>(`/web/v1/jobs/spark/overview-offheap-mem-usage`, {
        projectId: Number(jobId),
        runId: Number(runId),
        attemptId: attempt,
        startTime: startTime,
        endTime: endTime,
      });

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as SparkOffHeapMemoryUsage;
        const metaspace = result.metaSpace.map((v) => {
          return {
            x: v.timestamp,
            y: v.metric,
          };
        });

        const codeCache = result.codeCache.map((v) => {
          return {
            x: v.timestamp,
            y: v.metric,
          };
        });

        const compressedClass = result.compressedClass.map((v) => {
          return {
            x: v.timestamp,
            y: v.metric,
          };
        });

        return {
          message: "Success",
          name: "Off Heap Memory",
          stacked: true,
          seriesData: [
            { name: "MetaSpace", data: metaspace },
            { name: "Codecache", data: codeCache },
            { name: "Compressed Class", data: compressedClass },
          ],
          showLegend: true,
          loading: false,
          minValue: Math.min(metaspace[0].x),
          yFormatter: "size",
        };
      } else {
        return {
          ...defaultErrorTimeSeries,
        };
      }
    } catch (e) {
      return {
        ...defaultErrorTimeSeries,
      };
    }
  };

  getExecMetrics = async (jobId: number, runId: number, attempt: string, onSuccess: (r: ExecutorMetrics) => void) => {
    try {
      const response = this.webAPI.get<ExecMetricResponse>(`/web/v1/jobs/${jobId}/runs/${runId}/app-attempt/${attempt}/executors`);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as ExecutorMetrics;
        onSuccess(result);
      } else if (r.status === 401 && r.parsedBody) {
        const body = r.parsedBody as UnAuthorized;
        this.showUnAuthorizedError(body.requestedResource);
      } else {
        const err = this.getDefaultError("Fetch executor metrics");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  getSparkAppJobMetrics = async (
    jobId: number,
    runId: number,
    attemptId: string,
    startTime: number,
    endTime: number
  ): Promise<JobMetricsResponse> => {
    const requestName = "Get spark application job metrics";
    try {
      const response = this.webAPI.post<JobMetricsResponse>(
        `/api/v1/spark/job-metrics/jobs/${jobId}/run/${runId}/app-attempt/${attemptId}`,
        {
          startTime: startTime,
          endTime: endTime,
        }
      );

      const r = await response;
      if (r.parsedBody) {
        return r.parsedBody;
      } else if (r.status === 400 && r.parsedBody) {
        return r.parsedBody as IllegalParam;
      } else if (r.status === 401 && r.parsedBody) {
        return r.parsedBody as UnAuthorized;
      } else {
        return this.getDefaultError(requestName);
      }
    } catch (e) {
      const err = this.getDefaultError(requestName);
      return err;
    }
  };

  getSparkLogs = async (
    projectId: number,
    runId: number,
    taskId: number,
    lastOffset: number,
    onSuccess: (result: LogSearchResult) => void
  ) => {
    try {
      const args: RequestInit = {
        method: "get",
        credentials: "include",
        mode: "cors",
        headers: {
          "X-TIMEZONE": Intl.DateTimeFormat().resolvedOptions().timeZone,
          "X-OFFSET": lastOffset.toString(),
          Accept: "application/json",
          "Content-Type": "application/json",
        },
      };
      const response = this.webAPI.get<LogSearchResponse>(`/web/v1/logs/${projectId}/task/${taskId}`, args);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as LogSearchResult;
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IErrorType;
        this.showError(body.message);
      } else if (r.status === 401 && r.parsedBody) {
        const body = r.parsedBody as UnAuthorized;
        this.showUnAuthorizedError(body.requestedResource);
      } else {
        const err = this.getDefaultError("Log Search");
        this.showError(err.message);
      }
    } catch (e) {
      const err = this.getDefaultError("Log Search");
      this.showError(err.message);
    }
  };

  getSparkLogsPromise = async (projectId: number, runId: number, lastOffset: number): Promise<LogSearchResponse> => {
    try {
      const args: RequestInit = {
        method: "get",
        credentials: "include",
        mode: "cors",
        headers: {
          "X-TIMEZONE": Intl.DateTimeFormat().resolvedOptions().timeZone,
          "X-OFFSET": lastOffset.toString(),
          Accept: "application/json",
          "Content-Type": "application/json",
        },
      };
      const response = this.webAPI.get<LogSearchResponse>(`/web/v1/logs/${projectId}/runs/${runId}`, args);

      const r = await response;
      if (r.parsedBody) {
        return r.parsedBody;
      } else {
        const err: IErrorType = {
          statusCode: r.status,
          message: "Unable to get the logs",
        };
        return err;
      }
    } catch (e) {
      this.showError("Unable to process logout request. Please try again later.");
      return new Promise((resolve) => {
        const err: IErrorType = {
          statusCode: 500,
          message: "Unable to get the logs",
        };
        resolve(err);
      });
    }
  };

  searchSparkLogs = async (
    projectId: number,
    taskId: number,
    query: string,
    lastOffset: number,
    filters: {
      level: Array<string>;
      classname: Array<string>;
      startTime?: number;
      endTime?: number;
    }
  ): Promise<LogSearchResponse> => {
    try {
      let queryPost: any = {
        query: query,
        filters: {
          level: filters.level,
          classname: filters.classname,
        },
        lastOffset: lastOffset,
      };
      if (filters.startTime && filters.endTime) {
        queryPost = {
          query: query,
          filters: {
            level: filters.level,
            classname: filters.classname,
          },
          lastOffset: lastOffset,
          startTime: filters.startTime,
          endTime: filters.endTime,
        };
      }

      const response = this.webAPI.post<LogSearchResponse>(`/web/v1/logs/${projectId}/runs/${taskId}`, queryPost);

      const r = await response;

      if (r.parsedBody) {
        return r.parsedBody;
      } else {
        const err: IErrorType = {
          statusCode: r.status,
          message: "Unable to get the logs",
        };
        return err;
      }
    } catch (e) {
      this.showError("Unable to process logout request. Please try again later.");
      return new Promise((resolve) => {
        const err: IErrorType = {
          statusCode: 500,
          message: "Unable to get the logs",
        };
        resolve(err);
      });
    }
  };

  getSparkAppStageMetrics = async (jobId: number, runId: number, attemptId: string, sparkJobId: number): Promise<StageMetricsResponse> => {
    const requestName = "Get spark application stage metrics";
    try {
      const response = this.webAPI.get<StageMetricsResponse>(
        `/api/v1/spark/stage-metrics/jobs/${jobId}/run/${runId}/app-attempt/${attemptId}/spark-job/${sparkJobId}`
      );

      const r = await response;
      if (r.parsedBody) {
        return r.parsedBody;
      } else if (r.status === 400 && r.parsedBody) {
        return r.parsedBody as IllegalParam;
      } else if (r.status === 401 && r.parsedBody) {
        return r.parsedBody as UnAuthorized;
      } else {
        return this.getDefaultError(requestName);
      }
    } catch (e) {
      const err = this.getDefaultError(requestName);
      return err;
    }
  };

  getRuntimeExecutorMetrics = async (
    jobId: number,
    runId: number,

    execId: string,
    onSuccess: (r: DetailRuntimeMetrics) => void
  ) => {
    try {
      const response = this.webAPI.get<DetailRuntimeMetricsResponce>(`/web/v1/jobs/${jobId}/runs/${runId}/executor/${execId}/metrics`);
      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as DetailRuntimeMetrics;
        onSuccess(result);
        return result;
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else if (r.status === 401 && r.parsedBody) {
        const body = r.parsedBody as UnAuthorized;
        this.showUnAuthorizedError(body.requestedResource);
      } else {
        const err = this.getDefaultError("Run time Executor Metrics");
        this.showError(err.message);
      }
    } catch (e) {
      const err = this.getDefaultError("Run time Executor Metrics");
      this.showError(err.message);
    }
  };

  getAllExecutorMetrics = async (jobId: number, runId: number, onSuccess: (r: Array<RuntimeExecutorMetric>) => void) => {
    try {
      const response = this.webAPI.get<RuntimeExecutorMetricsResponce>(`/web/v1/jobs/${jobId}/runs/${runId}/executor-summary`);
      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as Array<RuntimeExecutorMetric>;
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else if (r.status === 401 && r.parsedBody) {
        const body = r.parsedBody as UnAuthorized;
        this.showUnAuthorizedError(body.requestedResource);
      } else {
        const err = this.getDefaultError("Detailed Run time Executor Metrics");
        this.showError(err.message);
      }
    } catch (e) {
      const err = this.getDefaultError("Detailed Run time Executor Metrics");
      this.showError(err.message);
    }
  };

  getAllExecutorStats = async (jobId: number, runId: number, attempt: string, onSuccess: (r: IExecutorsStats) => void) => {
    try {
      const response = this.webAPI.get<ExecutoreStatsResponse>(
        `/web/v1/spark/jobs/${jobId}/runs/${runId}/attempt/${attempt}/executors-overview`
      );
      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as IExecutorsStats;
        onSuccess(result);
      } else {
        const err = this.getDefaultError("Detailed Run time Executor stats");
        this.showError(err.message);
      }
    } catch (e) {
      const err = this.getDefaultError("Detailed Run time Executor stats");
      this.showError(err.message);
    }
  };

  getJobComparisonInfo = async (jobId: number, runId: number, onSuccess: (r: CompareRun) => void) => {
    try {
      const response = this.webAPI.post<JobComparisonInfoResponce>(`/web/v1/jobs/spark/compare-runs`, {
        projectId: jobId,
        runId: runId,
      });
      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as CompareRun;
        onSuccess(result);
      } else {
        const err = this.getDefaultError("Comparison view info");
        this.showError(err.message);
      }
    } catch (e) {
      const err = this.getDefaultError("Comparison view info");
      this.showError(err.message);
    }
  };
  getWorkerStats = async (jobId: number, runId: number, attempt: string, workerId: string, onSuccess: (r: IWorker) => void) => {
    try {
      const response = this.webAPI.get<AllWorkersResponse>(
        `/web/v1/spark/jobs/${jobId}/runs/${runId}/attempt/${attempt}/worker/${workerId}`
      );
      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as IWorker;
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else if (r.status === 401 && r.parsedBody) {
        const body = r.parsedBody as UnAuthorized;
        this.showUnAuthorizedError(body.requestedResource);
      } else {
        const err = this.getDefaultError("fetch all the workers");
        this.showError(err.message);
      }
    } catch (e) {
      const err = this.getDefaultError("fetch all the workers");
      this.showError(err.message);
    }
  };

  getAllCluster = async (onSuccess: (r: IAppCluster[]) => void, onError: (err: string) => void) => {
    try {
      const response = this.webAPI.get<IAppCluster[]>(`/web/v1/jobs/spark/allcluster`);
      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as IAppCluster[];
        onSuccess(result);
      } else {
        const err = this.getDefaultError("Get all cluster");
        this.showError(err.message);
        onError(err.message);
      }
    } catch (e) {
      const err = this.getDefaultError("Get all cluster");
      this.showError(err.message);
      onError(err.message);
    }
  };
}

export default new SparkService();
