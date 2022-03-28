export interface FileItem {
  name: string;
  lastModified: number;
  size: number;
  isDirectory: boolean;
  extension?: string;
}

export interface FileListing {
  hasMore: boolean;
  files: FileItem[];
  fsName: string;
  provider: string;
  marker?: string;
}

export interface FailedFileListing {
  name: string;
  provider: string;
  error: string;
}

export interface FileBrowserProps {
  connectionId: number;
  storageService: string;
  editMode: boolean;
  name: string;
  cwd: string[];
  items: FileItem[];
  closeEditDrawer: () => void;
  showEditDrawer: () => void;
}
