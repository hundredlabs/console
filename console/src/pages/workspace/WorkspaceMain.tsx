import * as React from "react";
import { Link } from "react-router-dom";
import GitHubButton from "react-github-btn";
import { Layout, Menu, Dropdown, Button, Space, Tooltip, Tag } from "antd";
import { connect } from "react-redux";
import { appLoaded } from "../../actions/app";
import { User, UserContext } from "../../store/User";
import { AppState } from "../../reducers";
import { updateLogin } from "../../actions/auth";
import { Dispatch } from "redux";
import { MdDashboard } from "react-icons/md";
import { FaDatabase, FaStream, FaFileInvoice } from "react-icons/fa";
import { BsPlusLg } from "react-icons/bs";
import { Hexagon } from "../../components/Icons/NavIcons";
import AuthService, { MemberProfile } from "../../services/AuthService";
import { history } from "../../configureStore";
import packageJson from "../../../package.json";
import CustomScroll from "react-custom-scroll";
import { getLocalStorage } from "../../services/Utils";
import WebService from "../../services/WebService";
import { ConsoleLogo } from "../../components/Icons/ConsoleLogo";
import "../../style/customScroll.css";

const { Content, Sider } = Layout;
const { SubMenu } = Menu;

interface IMainProps {
  index: string;
  content: React.ReactNode;
  isAppLoaded: boolean;
  slugId?: string;
  updateLogin?: typeof updateLogin;
  appLoaded?: typeof appLoaded;
  user?: User;
}

const mapStateToProps = (state: AppState) => ({
  isAppLoaded: state.app.isAppLoaded,
  user: state.auth,
});

const mapDispatchToProps = (dispatch: Dispatch) => ({
  updateLogin: (id: number, email: string, name: string, profile?: MemberProfile) => dispatch(updateLogin(id, email, name, profile)),
  appLoaded: () => dispatch(appLoaded()),
});
const getInitials = (text: string): string => {
  if (text.split(" ").length > 1) {
    let splits = text.split(" ");
    return splits[0].charAt(0).toUpperCase() + splits[1].charAt(0).toUpperCase();
  } else {
    return text.charAt(0).toUpperCase();
  }
};

const OrgThumbnailImg: React.FC<{ name: string; thumbnail?: string }> = ({ name, thumbnail }) => {
  const web = new WebService();
  return (
    <>
      {!thumbnail && <span className='org-initials'>{getInitials(name)}</span>}
      {thumbnail && <img src={`${web.getEndpoint()}${thumbnail}`} alt={`${name} thumbnail`} />}
    </>
  );
};

const WorkspaceMain: React.FC<IMainProps> = ({ index, content, updateLogin, isAppLoaded, appLoaded, user }) => {
  const context = React.useContext(UserContext);

  const [state, setState] = React.useState<{ collapsed: boolean }>({ collapsed: getLocalStorage("collaps") || false });

  const handleLogout = () => {
    AuthService.logOut().then((r) => {
      if (r.success) {
        console.log("moving to login page");
        context.updateUser(0, "", "", false, undefined);
        history.push("/login");
      }
    });
  };

  const trimText = (t: string) => {
    if (t.length > 10) {
      return `${t.substr(0, 10)}..`;
    } else {
      return t;
    }
  };

  let onCollaps = {};
  if (state.collapsed) {
    onCollaps = {
      display: "none",
    };
  }

  const orgMenu = (
    <Menu mode='horizontal'>
      <Menu.Item key='1' onClick={(e) => history.push(`/${context.currentUser.profile?.orgSlugId}/settings`)}>
        <span>Settings</span>
      </Menu.Item>

      <Menu.Item key='2' onClick={() => handleLogout()}>
        <span>Logout</span>
      </Menu.Item>
    </Menu>
  );

  return (
    <Layout className='main-app-wrapper'>
      <Sider trigger={null} collapsed={false} className='workspace-side-nav hex-sider-light'>
        <div className='workspace-header'>
          <Dropdown overlay={orgMenu} trigger={["click"]} overlayStyle={{ width: 200 }}>
            <div className={`logo`}>
              <Hexagon size={state.collapsed ? 23 : 20} />
              {user && (
                <span className='brand-name'>
                  {context.currentUser.profile && `${trimText(context.currentUser.profile.workspaceName)}`}
                </span>
              )}
            </div>
          </Dropdown>
          <Dropdown overlay={orgMenu} trigger={["click"]} overlayStyle={{ width: 200 }}>
            <div className={`logo`}>
              {context.currentUser.profile && (
                <OrgThumbnailImg name={context.currentUser.profile.orgName} thumbnail={context.currentUser.profile.orgThumbnail} />
              )}
            </div>
          </Dropdown>
        </div>
        <CustomScroll heightRelativeToParent='calc(100vh - 100px)'>
          <Menu theme='light' mode='inline' defaultSelectedKeys={[]} defaultOpenKeys={["0"]}>
            <Menu.Item
              key='11'
              className='center-name'
              onClick={(e) =>
                history.push(
                  `/${context.currentUser.profile?.orgSlugId}/workspace/${context.currentUser.profile?.workspaceId}/add-datasource`
                )
              }>
              <Button
                type='primary'
                icon={
                  <i style={{ fontSize: 12, marginTop: 4, marginRight: 5, color: "#fff" }}>
                    <BsPlusLg />
                  </i>
                }>
                Add Datasource
              </Button>
            </Menu.Item>
            <Menu.Item
              key='10'
              className='center-name'
              onClick={(e) =>
                history.push(`/${context.currentUser.profile?.orgSlugId}/workspace/${context.currentUser.profile?.workspaceId}/hosts`)
              }
              icon={
                <i style={{ fontSize: 18, marginTop: 4 }}>
                  <MdDashboard />
                </i>
              }>
              {context.currentUser.profile && <span>Sandboxes</span>}
            </Menu.Item>
            <SubMenu
              key='0'
              icon={
                <i className={`side-nav-icon`}>
                  <FaDatabase />
                </i>
              }
              title='Databases'>
              <Menu.Item key='4'>Option 4</Menu.Item>
              <Menu.Item key='8'>Option 8</Menu.Item>
            </SubMenu>
            <SubMenu
              key='1'
              icon={
                <i className={`side-nav-icon`}>
                  <FaFileInvoice />
                </i>
              }
              title='File Systems'>
              <Menu.ItemGroup key='g1' title='No File Systems'></Menu.ItemGroup>
            </SubMenu>
            <SubMenu
              key='2'
              icon={
                <i className={`side-nav-icon`}>
                  <FaStream />
                </i>
              }
              title='Streams'>
              <Menu.Item key='5'>Option 5</Menu.Item>
              <Menu.Item key='6'>Option 6</Menu.Item>
              <Menu.Item key='7'>Option 5</Menu.Item>
              <Menu.Item key='8'>Option 6</Menu.Item>
              <Menu.Item key='9'>Option 5</Menu.Item>
              <Menu.Item key='10'>Option 10</Menu.Item>
              <Menu.Item key='11'>Option 11</Menu.Item>
              <Menu.Item key='12'>Option 12</Menu.Item>
              <Menu.Item key='13'>Option 13</Menu.Item>
              <Menu.Item key='14'>Option 14</Menu.Item>
              <Menu.Item key='15'>Option 15</Menu.Item>
              <Menu.Item key='16'>Option 16</Menu.Item>
              <Menu.Item key='17'>Option 17</Menu.Item>
              <Menu.Item key='18'>Option 18</Menu.Item>
              <Menu.Item key='19'>Option 19</Menu.Item>
              <Menu.Item key='20'>Option 20</Menu.Item>
            </SubMenu>
          </Menu>
        </CustomScroll>

        <div className='brand-footer'>
          <Dropdown overlay={orgMenu} trigger={["click"]} overlayStyle={{ width: 200 }}>
            <div className='brand-logo-container'>
              <ConsoleLogo />
              <span className='brand-name'>Gigahex</span>
            </div>
          </Dropdown>
          <GitHubButton href='https://github.com/gigahexhq/console' data-show-count='true' aria-label='Star gigahexhq/console on GitHub'>
            Star
          </GitHubButton>
        </div>
      </Sider>
      <Layout className='site-layout'>
        <Content
          className='site-layout-background'
          style={{
            padding: "0px",
            minHeight: 280,
          }}>
          {content}
        </Content>
      </Layout>
    </Layout>
  );
};
export default connect(mapStateToProps, mapDispatchToProps)(WorkspaceMain);
