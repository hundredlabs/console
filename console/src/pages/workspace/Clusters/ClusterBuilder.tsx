import React from "react";
import RemoteClusterBuilder from "../../../components/clusters/RemoteClusterBuilder";

const ClusterBuilder: React.FC<{ orgSlugId: string; workspaceId: number }> = ({ orgSlugId, workspaceId }) => {
  return (
    <div className='workspace-wrapper'>
      <RemoteClusterBuilder orgSlugId={orgSlugId} workspaceId={workspaceId} />
    </div>
  );
};

export default ClusterBuilder;
