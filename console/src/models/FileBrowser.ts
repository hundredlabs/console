export interface FileItem {
  name: string;
  dateCreated: number;
  size: number;
  isDirectory: boolean;
  extension?: string;
}

export interface FileBrowserProps {
  storageService: "AWS-S3" | "EMR-HDFS" | "HDFS" | "S3-ENDPOINT";
  name: string;
  cwd: string[];
  items: FileItem[];
}
