import { Card, Tag } from "antd";
import React, { FC, useEffect, useState } from "react";
import { CloudCluster, ClusterProvider, WorkspaceApiKey } from "../../../services/Workspace";

import WorkspaceService from "../../../services/Workspace";
import { FormInstance } from "antd/lib/form";
import { ConnectionConfig } from "../../../components/connections/ConnectionConfig";
import { S3Connection } from "../../../components/connections/S3Connection";
import { PgConnection } from "../../../components/connections/PostgresConnection";

export const AddConnectionProvider: FC<{
  service: string;
  connectionForm: FormInstance;
  orgSlugId: string;
  workspaceId: number;
  onConnectionSave?: (conn: ConnectionConfig) => void;
}> = ({ service, connectionForm, orgSlugId, workspaceId, onConnectionSave }) => {
  const [providerForm, setProvider] = useState(false);
  const [processing, setProcessing] = useState<{
    isProcessing: boolean;
    isSaving: boolean;
    notifyBtnLoading: boolean;
    keys: WorkspaceApiKey[];
    clusters: CloudCluster[];
    errorMessage?: string;
  }>({
    isProcessing: false,
    isSaving: false,
    notifyBtnLoading: false,
    clusters: [],
    keys: [],
  });

  useEffect(() => {
    WorkspaceService.getWorkspaceKeys((keys) => {
      setProcessing({ ...processing, keys: keys });
    });
  }, []);

  const onClose = () => {
    setProvider(false);
  };

  return (
    <>
      <div style={{ padding: "10px" }}>
        {service === "s3" && <S3Connection />}
        {service === "postgres" && <PgConnection />}
      </div>
    </>
  );
};
