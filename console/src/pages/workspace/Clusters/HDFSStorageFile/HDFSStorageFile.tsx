import React, { FC } from "react";
import {
  Alert,
  Button,
  Cascader,
  Divider,
  Drawer,
  Dropdown,
  Empty,
  Input,
  Menu,
  message,
  Modal,
  Popconfirm,
  Space,
  Tooltip,
  Upload,
} from "antd";
import { CascaderOptionType, CascaderValueType } from "antd/lib/cascader";
import { LocalHDFSFileService, FileType, HDFSFile, HDFSStrogeFiles, FileStatus } from "../../../../services/LocalHDFSFileService";
import "./HDFSStorageFile.scss";

import { MdCreateNewFolder, MdFileUpload, MdFolder, MdInsertDriveFile, MdRefresh } from "react-icons/md";
import { UploadChangeParam, UploadFile } from "antd/lib/upload/interface";
import { InboxOutlined } from "@ant-design/icons";
import { activeTab } from "../HDFSClusterDashboard";
import { ClusterStatus, HDFSClusterMetric } from "../../../../services/Workspace";
import moment from "moment";
import { bytesToSize, bytesToDecimalSize } from "../../../../services/Utils";

const { Dragger } = Upload;

interface SelectedOpt {
  paths: string[];
  selectedOpts: CascaderOptionType;
}

interface ActionProps {
  path: string;
  name: string;
  root: boolean;
  oldPath: string;
  oldName: string;
  newName: string;
  fileType: FileType;
  isRenameModel: boolean;
  isCreateModel: boolean;
  isUploadModel: boolean;
  fileUpload: Array<UploadFile<any>>;
}

const CreateDirModel: FC<{
  onCreateDir: () => void;
  onDirNameChange: (v: string) => void;
  loading: boolean;
  action: ActionProps;
  onCancelModel: () => void;
}> = ({ action, onCreateDir, onDirNameChange, loading, onCancelModel }) => {
  const truncateString = (str: string, num: number) => {
    if (str.length > num) {
      return str.slice(0, num) + "...";
    } else {
      return str;
    }
  };
  return (
    <Modal
      keyboard={true}
      title={`Create a Directory`}
      className='create-file-model'
      visible={action.isCreateModel}
      okText='Create'
      onOk={onCreateDir}
      okButtonProps={{ loading: loading, className: "btn-action" }}
      onCancel={onCancelModel}>
      <div className='create-file-input'>
        <Input
          onPressEnter={onCreateDir}
          addonBefore={`${action.path === "/" ? "/" : "/" + truncateString(action.path, 30) + "/"}`}
          value={action.name}
          placeholder={"dirName or dirName/dirName/..."}
          onChange={(e) => onDirNameChange(e.target.value)}
        />
      </div>
    </Modal>
  );
};

const UploadFileModel: FC<{
  action: ActionProps;
  onCloseUploadModel: () => void;
  onUploadInfo: (e: UploadChangeParam<UploadFile<any>>) => void;
  clusterId: number;
  metric: HDFSClusterMetric;
  onUploadTar: (v: string) => void;
}> = ({ action, onCloseUploadModel, onUploadInfo, onUploadTar, clusterId, metric }) => {
  const srvPort = metric.processes.find((m, i) => m.name === "namenode").port;
  const truncateString = (str: string, num: number) => {
    if (str.length > num) {
      return str.slice(0, num) + "...";
    } else {
      return str;
    }
  };
  return (
    <Modal
      keyboard={true}
      onOk={onCloseUploadModel}
      okButtonProps={{ style: { display: "none" } }}
      className='upload-file-model'
      title={`Upload File in Dir : ${truncateString(action.path, 35)}`}
      visible={action.isUploadModel}
      onCancel={onCloseUploadModel}>
      <div className='upload-file-input'>
        {action.root && <Input placeholder='/dir or /path/to/dir' value={action.path} onChange={(e) => onUploadTar(e.target.value)} />}
        {!action.path && <Alert message='To upload a file, put / ' showIcon type='info' />}
        <Dragger
          onChange={onUploadInfo}
          directory={false}
          method='PUT'
          disabled={!action.path ? true : false}
          multiple={true}
          fileList={action.fileUpload}
          showUploadList={true}
          action={(e) => {
            return new Promise((resolve, reject) => {
              const uploadPath = action.path === "/" ? `/${e.name}` : `${action.path}/${e.name}`;
              resolve(
                `${process.env.REACT_APP_API_ENDPOINT}/web/v1/hadoop/${clusterId}/webhdfs/v1${uploadPath}?op=CREATE&createflag=&createparent=true&overwrite=true&user.name=user_name`
              );
            });
          }}
          withCredentials={true}
          name='hdfsFile'>
          <p className='ant-upload-drag-icon'>
            <InboxOutlined />
          </p>
          <p className='ant-upload-text'>Click or drag file to this area to upload</p>
          <p className='ant-upload-hint'>
            Support for a single or bulk upload. Strictly prohibit from uploading company data or other band files
          </p>
        </Dragger>
      </div>
    </Modal>
  );
};

const FileStatusDrawer: FC<{ fileStatus: HDFSFile }> = ({ fileStatus }) => {
  return (
    <Space direction='vertical'>
      <div>
        <div className='key-label'>LAST ACCESSED</div>
        <div className='key-value'>{fileStatus?.accessTime > 0 ? moment(fileStatus?.accessTime).fromNow() : "none"}</div>
      </div>
      <div>
        <div className='key-label'>BLOCK SIZE</div>
        <div className='key-value'>{bytesToDecimalSize(fileStatus?.blockSize)}</div>
      </div>
      <div>
        <div className='key-label'>OWNER</div>
        <div className='key-value'>{fileStatus?.owner}</div>
      </div>
      <div>
        <div className='key-label'>GROUP</div>
        <div className='key-value'>{fileStatus?.group}</div>
      </div>
      <div>
        <div className='key-label'>SIZE</div>
        <div className='key-value'>{bytesToSize(fileStatus?.length)}</div>
      </div>
      <div>
        <div className='key-label'>REPLICATION</div>
        <div className='key-value'>{fileStatus.replication}</div>
      </div>
    </Space>
  );
};

const HDFSStorageFile: FC<{ activeTab: activeTab; clusterId: number; status: ClusterStatus; metric: HDFSClusterMetric }> = ({
  clusterId,
  activeTab,
  status,
  metric,
}) => {
  const [loading, setLoading] = React.useState<boolean>(false);
  const [options, setOptions] = React.useState<CascaderOptionType[]>([]);
  const [refres, setRefres] = React.useState<boolean>(false);

  const [statusDrawer, setStatusDrawer] = React.useState<boolean>(false);
  const [fileStatus, setFileStatus] = React.useState<HDFSFile>(null);
  const [selectedOpts, setSelectedOpts] = React.useState<SelectedOpt>({
    paths: [],
    selectedOpts: [],
  });

  const [action, setAction] = React.useState<ActionProps>({
    path: "",
    name: "",
    root: false,
    oldPath: "",
    oldName: "",
    newName: "",
    isCreateModel: false,
    isUploadModel: false,
    isRenameModel: false,
    fileType: "EMPTY",
    fileUpload: [],
  });

  const renameOk = () => {
    if (action.newName === action.oldName) {
      setAction({ ...action, isRenameModel: false });
      return;
    }
    setLoading(true);
    const hdfsFileService = new LocalHDFSFileService();
    const paths = action.oldPath.split("/");
    paths.pop();
    const newPath = paths.length > 0 ? `${paths.join("/")}/${action.newName}` : action.newName;
    hdfsFileService.renameStorageFile(
      action.oldPath,
      newPath,
      clusterId,
      (r) => {
        if (r.boolean) {
          if (!paths.length) {
            setLoading(false);
            setAction({ ...action, oldName: "", newName: "", path: "", isRenameModel: false });
            setRefres(!refres);
          } else {
            setAction({ ...action, oldName: "", newName: "", path: "", isRenameModel: false });
            let opts = selectedOpts.selectedOpts;
            opts.pop();
            onChange(paths, opts);
            setLoading(false);
          }
        } else {
          setLoading(false);
          setAction({ ...action, oldName: "", newName: "", path: "", isRenameModel: false });
          message.error("Something worng");
        }
      },
      (err) => {
        setLoading(false);
        setAction({ ...action, oldName: "", newName: "", path: "", isRenameModel: false });
        message.error(err);
      }
    );
  };

  const deleteItem = (data: CascaderOptionType[], path: string) => {
    var r = data.filter(function (o: CascaderOptionType) {
      if (o.children) o.children = deleteItem(o.children, path);
      return o.path != path;
    });
    return r;
  };

  const deleteFile = (path: string, fileType: FileType, fileName: string) => {
    let sltPath = path.split("/");
    const p = sltPath.pop();
    setLoading(true);
    const hdfsFileService = new LocalHDFSFileService();
    hdfsFileService.deleteStorageFile(
      path,
      clusterId,
      (r) => {
        if (r.boolean) {
          setLoading(false);
          if (!sltPath.length) {
            setRefres(!refres);
          } else {
            const newData = deleteItem(options, p || "");
            setOptions(newData);
          }
          message.success(`Deleted " ${fileType.toLowerCase()} : ${fileName} " Success`);
        } else {
          setLoading(false);
          message.error("Something worng");
        }
      },
      (err) => {
        setLoading(false);
        message.error(err);
      }
    );
  };

  const createDir = () => {
    setLoading(true);
    const hdfsFileService = new LocalHDFSFileService();
    const path = action.path === "/" ? action.name : `${action.path}/${action.name}`;
    hdfsFileService.createStorageFile(
      path,
      clusterId,
      (r) => {
        if (r.boolean) {
          if (action.path === "/") {
            setLoading(false);
            setAction({ ...action, isCreateModel: false, name: "", path: "" });
            setRefres(!refres);
          } else {
            setLoading(false);
            setAction({ ...action, isCreateModel: false, name: "", path: "" });
            onChange(selectedOpts.paths, selectedOpts.selectedOpts);
          }
          message.success(`Directory /${path} has been successfully created.`);
        } else {
          setLoading(false);
          setAction({ ...action, isCreateModel: false, name: "", path: "" });
          message.error("Something worng Please Try again later");
        }
      },
      (err) => {
        setLoading(false);
        setAction({ ...action, isCreateModel: false, name: "", path: "" });
        message.error(err);
      }
    );
  };

  const onUploadInfo = (e: UploadChangeParam<UploadFile<any>>) => {
    setAction({ ...action, fileUpload: [...e.fileList] });
    if (e.file.status !== "uploading") {
    }
    if (e.file.status === "done") {
      message.success(`${e.file.name} file uploaded successfully`);
      if (action.root) {
        setAction({ ...action, path: "", fileUpload: [], isUploadModel: false });
        setRefres(!refres);
      } else {
        setAction({ ...action, path: "", fileUpload: [], isUploadModel: false });
        onChange(selectedOpts.paths, selectedOpts.selectedOpts);
      }
    } else if (e.file.status === "error") {
      setAction({ ...action, path: "", isUploadModel: false, fileUpload: [] });
      message.error(`${e.file.name} file upload failed.`);
    }
  };

  const openFileStatus = (path: string, fileName: string, filetype: FileType) => {
    setLoading(true);
    const hdfsFileService = new LocalHDFSFileService();
    hdfsFileService.getFileStatus(
      path,
      clusterId,
      (r) => {
        if (r) {
          setStatusDrawer(true);
          setFileStatus(r.FileStatus);
          console.log(r);
          setLoading(false);
        } else {
          setLoading(false);
          setStatusDrawer(false);
          message.error("Something wents worng please try again later");
        }
      },
      (err) => {
        setLoading(false);
        setStatusDrawer(false);
        message.error(err);
      }
    );
  };

  const onCloseStatusDrawer = () => {
    setStatusDrawer(false);
  };

  const onUploadModel = (path: string, root: boolean) => {
    setAction({ ...action, path: path, isUploadModel: true, root: root, isCreateModel: false, isRenameModel: false });
  };

  const onCreateModal = (path: string) => {
    setAction({ ...action, name: "", path: path, isCreateModel: true, isRenameModel: false, isUploadModel: false });
  };

  const onDirNameChange = (v: string) => {
    setAction({ ...action, name: v });
  };
  const onCancelCreatetModel = () => {
    setAction({ ...action, isCreateModel: false, name: "", path: "" });
  };
  const onRenameModal = (path: string, fileName: string, fileType: FileType) => {
    setAction({
      ...action,
      fileType: fileType,
      oldName: fileName,
      newName: fileName,
      oldPath: path,
      isRenameModel: true,
      isCreateModel: false,
      isUploadModel: false,
    });
  };
  const onCloseUploadModel = () => {
    setAction({ ...action, isUploadModel: false, path: "", fileUpload: [] });
  };

  const onUploadTar = (dis: string) => {
    setAction({ ...action, path: dis });
  };

  const contextMenu = (r: CascaderOptionType) => (
    <Menu className='hdfs-context-menu'>
      {r.fileType === "DIRECTORY" && (
        <>
          <Menu.Item key='1' onClick={() => onCreateModal(r.path)}>
            Create Directory
          </Menu.Item>
          <Menu.Item key='6' onClick={() => onUploadModel(`/${r.path}`, false)}>
            Upload File
          </Menu.Item>
        </>
      )}

      <Menu.Item key='3' onClick={() => onRenameModal(r.path, r.fileName, r.fileType)}>
        Rename
      </Menu.Item>
      {r.fileType === "FILE" && (
        <Menu.Item key='4' onClick={() => openFileStatus(r.path, r.fileName, r.fileType)}>
          Status
        </Menu.Item>
      )}
      <Divider style={{ margin: 0 }} />
      <Menu.Item key='8'>
        <Popconfirm
          title={
            <div>
              Are you sure want to Delete ? <b>{r.fileType}</b> : <b>{r.fileName}</b>
            </div>
          }
          placement='top'
          onConfirm={() => deleteFile(r.path, r.fileType, r.fileName)}
          arrowPointAtCenter={false}
          okText='Yes'
          cancelText='No'>
          Delete
        </Popconfirm>
      </Menu.Item>
    </Menu>
  );

  const onChange = (value: CascaderValueType, selectedOptions: any) => {
    if (status === "running") {
      setSelectedOpts({ paths: value as string[], selectedOpts: selectedOptions });
      const targetOption = selectedOptions[selectedOptions.length - 1];
      if (targetOption.fileType === "FILE" || targetOption.fileType === "EMPTY") return;
      targetOption.loading = true;

      let path: string = (value as string[])[0];
      if (value.length > 1) {
        path = value.join("/");
      }
      const hdfsFileService = new LocalHDFSFileService();
      hdfsFileService.listAllHDFSStorageFiles(
        `${path}`,
        clusterId,
        (res) => {
          targetOption.loading = false;
          if (res.FileStatuses.FileStatus.length > 0) {
            const files = res.FileStatuses.FileStatus.map((r) => {
              if (r.type === "FILE") {
                return {
                  label: (
                    <Dropdown
                      overlay={() => contextMenu({ fileType: r.type, path: `${path}/${r.pathSuffix}`, fileName: r.pathSuffix })}
                      trigger={["contextMenu"]}>
                      <div>
                        <MdInsertDriveFile style={{ marginRight: 8 }} />
                        {r.pathSuffix}
                      </div>
                    </Dropdown>
                  ),
                  fileType: r.type,
                  isLeaf: true,
                  path: r.pathSuffix,
                };
              }
              if (r.type === "DIRECTORY") {
                return {
                  label: (
                    <Dropdown
                      overlay={() => contextMenu({ fileType: r.type, path: `${path}/${r.pathSuffix}`, fileName: r.pathSuffix })}
                      trigger={["contextMenu"]}>
                      <div>
                        <MdFolder style={{ marginRight: 8 }} />
                        {r.pathSuffix}
                      </div>
                    </Dropdown>
                  ),
                  fileType: r.type,
                  isLeaf: false,
                  path: r.pathSuffix,
                };
              }
            });

            targetOption.children = [...files];
          } else {
            targetOption.loading = false;
            targetOption.children = [
              {
                label: "",
                fileType: "EMPTY",
                path: "",
              },
            ];
          }
        },
        (err) => {
          targetOption.loading = false;
          message.error(err);
        }
      );
    }
  };

  const getLocalHDFSFile = () => {
    if (status === "running") {
      setLoading(true);
      const hdfsFileService = new LocalHDFSFileService();
      hdfsFileService.listAllHDFSStorageFiles(
        "/",
        clusterId,
        (hdfsFiles) => {
          const opts = hdfsFiles.FileStatuses.FileStatus.map((r) => {
            if (r.type === "FILE") {
              return {
                label: (
                  <Dropdown
                    overlay={() => contextMenu({ fileType: r.type, path: r.pathSuffix, fileName: r.pathSuffix })}
                    trigger={["contextMenu"]}>
                    <div>
                      <MdInsertDriveFile style={{ marginRight: 8 }} />
                      {r.pathSuffix}
                    </div>
                  </Dropdown>
                ),
                fileType: r.type,
                path: r.pathSuffix,
              };
            }
            if (r.type === "DIRECTORY") {
              return {
                label: (
                  <Dropdown
                    overlay={() => contextMenu({ fileType: r.type, path: r.pathSuffix, fileName: r.pathSuffix })}
                    trigger={["contextMenu"]}>
                    <div>
                      <MdFolder style={{ marginRight: 8 }} />
                      {r.pathSuffix}
                    </div>
                  </Dropdown>
                ),
                fileType: r.type,
                isLeaf: false,
                path: r.pathSuffix,
              };
            }
          });

          setOptions(opts);
          setLoading(false);
        },
        (err) => {
          message.error(err);
          setLoading(false);
        }
      );
    }
  };

  React.useEffect(() => {
    getLocalHDFSFile();
  }, [activeTab, refres, status]);

  if (status === "running" && !options.length) {
    return (
      <div className='empty-file-contaier'>
        <Empty description='No directories have been created'>
          <Button type='primary' className='create-dir-btn' onClick={() => onCreateModal("/")}>
            Create directory
          </Button>
          <Button type='primary' className='upload-file-btn' onClick={() => onUploadModel("/", true)}>
            Upload file
          </Button>
        </Empty>
        <UploadFileModel
          onUploadTar={onUploadTar}
          action={action}
          onCloseUploadModel={onCloseUploadModel}
          onUploadInfo={onUploadInfo}
          clusterId={clusterId}
          metric={metric}
        />
        <CreateDirModel
          onCreateDir={createDir}
          action={action}
          onDirNameChange={onDirNameChange}
          onCancelModel={onCancelCreatetModel}
          loading={loading}
        />
      </div>
    );
  } else if (status === "running" && options.length) {
    return (
      <div className='workspace-wrapper hdfs-storage-location'>
        <div className='location-container'>
          <div className='files-menu-items'>
            <div id='cascader-area'>
              <Cascader
                popupVisible
                fieldNames={{ value: "path" }}
                options={options}
                value={selectedOpts.paths}
                dropdownRender={(menus) => (
                  <div>
                    <div className='dropdown-header'>
                      <Tooltip title='Create Directory'>
                        <Button type='text' style={{ marginLeft: 5 }} icon={<MdCreateNewFolder />} onClick={() => onCreateModal("/")} />
                      </Tooltip>
                      <Tooltip title='Upload File'>
                        <Button type='text' style={{ marginLeft: 5 }} onClick={() => onUploadModel("/", true)} icon={<MdFileUpload />} />
                      </Tooltip>
                      <Tooltip title='Refresh HDFS Browser'>
                        <Button type='text' icon={<MdRefresh />} onClick={() => setRefres(!refres)} />
                      </Tooltip>
                    </div>
                    <div className='dropdown-render'>{menus}</div>
                  </div>
                )}
                showSearch={false}
                changeOnSelect={true}
                onChange={onChange}
                getPopupContainer={() => document.getElementById("cascader-area")}
              />
            </div>
          </div>
        </div>
        <Drawer
          title='File Status'
          placement='right'
          width={300}
          className='file-status-drawer'
          onClose={onCloseStatusDrawer}
          visible={statusDrawer}>
          {fileStatus ? <FileStatusDrawer fileStatus={fileStatus} /> : <div>Status not found</div>}
        </Drawer>
        <Modal
          className='rename-file-model'
          title={`Rename the ${action.fileType.toLowerCase()} : ${action.oldName}`}
          visible={action.isRenameModel}
          okText='Rename'
          onOk={renameOk}
          keyboard={true}
          okButtonProps={{ className: "btn-action", loading: loading }}
          onCancel={() => setAction({ ...action, isRenameModel: false })}>
          <div className='rename-file-input'>
            <Input
              onPressEnter={renameOk}
              placeholder={`Rename the ${action.fileType.toLowerCase()}`}
              value={action.newName}
              onChange={(e) => setAction({ ...action, newName: e.target.value })}
            />
          </div>
        </Modal>
        <UploadFileModel
          onUploadTar={onUploadTar}
          action={action}
          onCloseUploadModel={onCloseUploadModel}
          onUploadInfo={onUploadInfo}
          clusterId={clusterId}
          metric={metric}
        />
        <CreateDirModel
          onCreateDir={createDir}
          action={action}
          onDirNameChange={onDirNameChange}
          onCancelModel={onCancelCreatetModel}
          loading={loading}
        />
      </div>
    );
  } else {
    return (
      <div className='hdfs-alert-info'>
        <Alert showIcon message={"Cluster is not running , Start the Cluster to Browse the file."} />
      </div>
    );
  }
  return <></>;
};

export default HDFSStorageFile;
