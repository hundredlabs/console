import WebService from "./WebService";
import { IErrorHandler } from "./IErrorHander";
import { parse, HTMLElement } from "node-html-parser";

export interface SparkAppAttempt {
  startTime: string;
  endTime: string;
  lastUpdated: string;
  duration: number;
  sparkUser: string;
  completed: boolean;
  appSparkVersion: string;
  startTimeEpoch: number;
  lastUpdatedEpoch: number;
  endTimeEpoch: number;
}
``;
export interface SparkApplicationInstance {
  id: string;
  name: string;
  attempts: SparkAppAttempt[];
}
export interface SparkWorker {
  id: string;
  workerState: string;
  ipWithPort: string;
  cpusAvailable: number;
  cpusUsed: number;
  memAvailable: string;
  memUsed: string;
}
export interface SparkRuntimeStats {
  appsRunning: number;
  appsCompleted: number;
  workers: SparkWorker[];
}

export const SparkConstants = {
  historyServer: "Spark History Server",
  masterWeb: "Master Web UI",
  masterWebDefaultPort: 8080,
  defaultHistoryServerPort: 18080,
};

export class LocalSparkMasterWeb extends IErrorHandler {
  protected webAPI: WebService;
  protected port: number;

  constructor(port: number) {
    super();
    this.port = port;
    this.webAPI = new WebService("localhost", port, "http", false);
  }

  is_number(char: string) {
    return !isNaN(parseInt(char));
  }

  parseWorkerInfo(info: string): SparkWorker {
    const infoArr: string[] = info
      .trim()
      .split("\n")
      //.map((l) => l.trim())
      .filter((line) => line.trim() !== "")
      .map((l) => l.trim());

    const w: SparkWorker = {
      id: infoArr[0],
      ipWithPort: infoArr[1],
      workerState: infoArr[2],
      cpusAvailable: Number(infoArr[3].substring(0, infoArr[3].indexOf("(")).trim()),
      cpusUsed: Number(infoArr[3].substring(infoArr[3].indexOf("(") + 1, infoArr[3].indexOf("Used")).trim()),
      memAvailable: infoArr[4],
      memUsed: infoArr[5].substring(infoArr[5].indexOf("(") + 1, infoArr[5].indexOf("Used")).trim(),
    };
    return w;
  }

  getRuntimeStats = async (onSuccess: (r: SparkRuntimeStats) => void) => {
    try {
      const sparkMasterUrl = `http://localhost:${this.port}/`;
      const args: RequestInit = {
        method: "get",
        credentials: "include",
        mode: "cors",
        headers: {
          Accept: "*/*",
          "Content-Type": "text/html",
        },
      };
      const sparkRuntimeMetric = new Request(sparkMasterUrl, args);
      fetch(sparkRuntimeMetric)
        .then((response) => response.text())
        .then((data) => {
          const root = parse(data);
          const activeApps: string = root.querySelector("span.collapse-aggregated-activeApps > h4 > a").innerHTML;
          const activeAppCount = Array.from(activeApps).filter((ch) => this.is_number(ch));
          const completedApps = root.querySelector("span.collapse-aggregated-completedApps > h4 > a").innerHTML;
          const completedAppCount = Array.from(completedApps).filter((ch) => this.is_number(ch));

          //Get the worker nodes html
          const workersNode = root.querySelector("div.aggregated-workers > table > tbody").childNodes;
          const trNode = workersNode.filter((n) => (n as HTMLElement).rawTagName === "tr");

          const workers = trNode.map((n) => this.parseWorkerInfo(n.innerText));
          const rr: SparkRuntimeStats = {
            appsRunning: Number(activeAppCount),
            appsCompleted: Number(completedAppCount),
            workers: workers,
          };
          onSuccess(rr);
        })
        .catch((e) => {
          this.notifyError(`Failed processing request for Spark cluster`);
        });
      // const data = fs.readFileSync("/Users/shad/.gigahex/master.html", "utf8");
    } catch (e) {
      this.notifyError("Unable to fetch spark applications from history server. Make sure its running.");
    }
  };
}

export class LocalSparkHistory extends IErrorHandler {
  protected webAPI: WebService;

  constructor(port: number) {
    super();
    this.webAPI = new WebService("localhost", port, "http", false);
  }

  listSparkApplications = async (onSuccess: (r: SparkApplicationInstance[]) => void, onFailure: () => void) => {
    try {
      const response = this.webAPI.get<SparkApplicationInstance[]>(`/api/v1/applications`);

      const r = await response;
      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as SparkApplicationInstance[];
        onSuccess(result);
      } else {
        const err = this.getDefaultError("list spark applications");

        this.notifyError(err.message);
        onFailure();
      }
    } catch (e) {
      this.notifyError("Unable to fetch spark applications from history server. Make sure its running.");
      onFailure();
    }
  };
}
