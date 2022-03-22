import React, { FC } from "react";
import { Route } from "react-router-dom";
import NoMatch from "../components/NoMatch";
import { MainRedirect } from "../pages/Main";

import Oauth from "../components/Login/Oauth";
import UserApp from "../pages/UserApp";
import WorkspaceMain from "../pages/workspace/WorkspaceMain";
import OrgSetting from "../pages/workspace/OrgSettings";
import Clusters from "../pages/workspace/Clusters/Clusters";
import WorkspaceList from "../pages/workspace/WorkspaceList";
import CreateOrgWorkspace from "../pages/workspace/CreateOrgWorkspace/CreateOrgWorkspace";
import { WorkspaceOverview } from "../pages/workspace/Overview";
import WorkspaceDashboard from "../pages/workspace/WorkspaceDashboard/WorkspaceDashboard";
import LoginWithEmail from "../components/Login/LoginWithEmail";
import ClusterDashboard from "../pages/workspace/Clusters/ClusterDashboard";
import SparkClusterDashboard from "../pages/workspace/Clusters/SparkClusterDashboard";
import ClusterBuilder from "../pages/workspace/Clusters/ClusterBuilder";
import HostsList from "../pages/workspace/HostList/HostList";
import KafkaClusterDashboard from "../pages/workspace/Clusters/KafkaClusterDashboard";
import HDFSClusterDashboard from "../pages/workspace/Clusters/HDFSClusterDashboard";
import AddDatasource from "../pages/workspace/AddDatasource/AddDatasource";
import { FileBrowser, FileManager } from "../components/FileBrowser/FileBrowser";
import DatabaseBrowse from "../pages/workspace/DatabaseBrowser/DatabaseBrowser";

const routes = (
  <div>
    <UserApp>
      <Route exact path='/' component={MainRedirect} />
      <Route
        exact
        path='/:slugId/workspace/:workspaceId/new-cluster'
        render={(props) => (
          <WorkspaceMain
            index='2'
            content={<ClusterBuilder orgSlugId={props.match.params.slugId} workspaceId={Number(props.match.params.workspaceId)} />}
            {...props}
          />
        )}
      />

      <Route path='/login' render={(props) => <LoginWithEmail search={props.location.search} />} />

      <Route
        exact
        path='/oauth/:provider'
        render={(props) => <Oauth provider={props.match.params.provider} location={props.location.search} />}
      />
      <Route path='/onboard' render={(props) => <CreateOrgWorkspace {...props} />} />
      <Route path='/workspace/dashboard' render={(props) => <WorkspaceMain index='0' slugId='223' content={<WorkspaceDashboard />} />} />
      <Route path='/workspaces' exact render={(props) => <WorkspaceMain index='0' slugId='' content={<WorkspaceOverview slugId='' />} />} />

      <Route
        path='/:slugId/workspaces'
        exact
        render={(props) => <WorkspaceMain index='0' slugId={props.match.params.slugId} content={<WorkspaceList />} {...props} />}
      />
      <Route
        path='/:slugId/workspace/:workspaceId/clusters'
        exact
        render={(props) => <WorkspaceMain index='0' slugId={props.match.params.slugId} content={<Clusters />} {...props} />}
      />
      <Route
        path='/:slugId/workspace/:workspaceId/hosts'
        exact
        render={(props) => <WorkspaceMain index='10' slugId={props.match.params.slugId} content={<HostsList />} {...props} />}
      />
      <Route
        path='/:slugId/workspace/:workspaceId/s3'
        exact
        render={(props) => <WorkspaceMain index='10' slugId={props.match.params.slugId} content={<FileManager />} {...props} />}
      />
      <Route
        path='/:slugId/settings'
        exact
        render={(props) => <WorkspaceMain index='1' slugId={props.match.params.slugId} content={<OrgSetting />} {...props} />}
      />

      <Route
        path='/:slugId/workspace/:workspaceId/clusters/:clusterId'
        exact
        render={(props) => (
          <WorkspaceMain
            index='2'
            slugId={props.match.params.slugId}
            content={
              <ClusterDashboard
                workspaceId={Number(props.match.params.workspaceId)}
                orgSlugId={props.match.params.slugId}
                clusterId={Number(props.match.params.clusterId)}
              />
            }
            {...props}
          />
        )}
      />
      <Route
        path='/:slugId/workspace/:workspaceId/clusters/:clusterId/spark'
        exact
        render={(props) => (
          <WorkspaceMain
            index='2'
            slugId={props.match.params.slugId}
            content={
              <SparkClusterDashboard
                workspaceId={Number(props.match.params.workspaceId)}
                orgSlugId={props.match.params.slugId}
                clusterId={Number(props.match.params.clusterId)}
              />
            }
            {...props}
          />
        )}
      />
      <Route
        path='/:slugId/workspace/:workspaceId/clusters/:clusterId/kafka'
        exact
        render={(props) => (
          <WorkspaceMain
            index='2'
            slugId={props.match.params.slugId}
            content={
              <KafkaClusterDashboard
                workspaceId={Number(props.match.params.workspaceId)}
                orgSlugId={props.match.params.slugId}
                clusterId={Number(props.match.params.clusterId)}
              />
            }
            {...props}
          />
        )}
      />
      <Route
        path='/:slugId/workspace/:workspaceId/clusters/:clusterId/hadoop'
        exact
        render={(props) => (
          <WorkspaceMain
            index='2'
            slugId={props.match.params.slugId}
            content={
              <HDFSClusterDashboard
                workspaceId={Number(props.match.params.workspaceId)}
                orgSlugId={props.match.params.slugId}
                clusterId={Number(props.match.params.clusterId)}
              />
            }
            {...props}
          />
        )}
      />

      <Route
        path='/:slugId/workspace/:workspaceId/add-datasource'
        exact
        render={(props) => (
          <WorkspaceMain
            index='11'
            slugId={props.match.params.slugId}
            content={<AddDatasource orgSlugId={props.match.params.slugId} workspaceId={Number(props.match.params.workspaceId)} />}
            {...props}
          />
        )}
      />
      <Route
        path='/:slugId/workspace/:workspaceId/database/:databaseId'
        exact
        render={(props) => (
          <WorkspaceMain
            index='0'
            slugId={props.match.params.slugId}
            content={
              <DatabaseBrowse
                orgSlugId={props.match.params.slugId}
                workspaceId={Number(props.match.params.workspaceId)}
                databaseId={Number(props.match.params.workspaceId)}
              />
            }
            {...props}
          />
        )}
      />
    </UserApp>
  </div>
);

export default routes;
