import * as React from "react";
import { Link } from "react-router-dom";
import { MenuFoldOutlined, DownOutlined } from "@ant-design/icons";
import { Layout, Menu, Dropdown, Button, Space, Tooltip, Tag } from "antd";
import { connect } from "react-redux";
import { appLoaded } from "../../actions/app";
import { User, UserContext } from "../../store/User";
import { AppState } from "../../reducers";
import { updateLogin } from "../../actions/auth";
import { Dispatch } from "redux";
import { MdQuestionAnswer, MdDashboard, MdDns, MdBook, MdKeyboardArrowDown } from "react-icons/md";
import { FaDatabase, FaStream, FaFileInvoice } from "react-icons/fa";
import { BsPlusLg, BsArrowRight } from "react-icons/bs";
import { Hexagon } from "../../components/Icons/NavIcons";
import AuthService, { MemberProfile } from "../../services/AuthService";
import { history } from "../../configureStore";
import packageJson from "../../../package.json";
import CustomScroll from "react-custom-scroll";
import { getLocalStorage, setLocalStorage } from "../../services/Utils";
import WebService from "../../services/WebService";
import { ConsoleLogo } from "../../components/Icons/ConsoleLogo";
import "../../style/customScroll.css";

const { Content, Sider, Header } = Layout;
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
  const toggle = () => {
    setState({
      collapsed: !state.collapsed,
    });
    setLocalStorage("collaps", !getLocalStorage("collaps"));
  };

  const createMenu = (
    <Menu>
      {/* disabled for free version */}
      {/* <Menu.Item key='create:1'>
        {context.currentUser.profile && (
          <Link to={`/${context.currentUser.profile.orgSlugId}/workspace/${context.currentUser.profile.workspaceId}/deploy-job`}>
            Add Job
          </Link>
        )}
      </Menu.Item> */}
      <Menu.Item key='create:3'>
        {context.currentUser.profile && (
          <Link to={`/${context.currentUser.profile.orgSlugId}/workspace/${context.currentUser.profile.workspaceId}/new-cluster`}>
            Add Cluster
          </Link>
        )}
      </Menu.Item>
    </Menu>
  );

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

  const HelpMenu = (
    <Menu mode='horizontal'>
      <Menu.Item
        key='9'
        style={{ display: "flex" }}
        icon={
          <i style={{ fontSize: 16, marginTop: 4, color: "grey", marginRight: 5 }}>
            <MdBook />
          </i>
        }
        onClick={(e) => {}}>
        <div>
          <a href='https://gigahex.com/docs' target='_blank'>
            <span>Documentation</span>
          </a>
        </div>
      </Menu.Item>
      <Menu.Item
        key='10'
        style={{ display: "flex" }}
        onClick={(e) => {}}
        icon={
          <i style={{ fontSize: 16, marginTop: 4, marginRight: 5 }}>
            <MdQuestionAnswer />
          </i>
        }>
        <div>
          <a href='https://github.com/GigahexHQ/gigahex/issues/new/choose' target='_blank'>
            <span>Community Support</span>
          </a>
        </div>
      </Menu.Item>
    </Menu>
  );

  return (
    <Layout className='main-app-wrapper'>
      <Sider trigger={null} collapsible collapsed={true} className='workspace-side-nav hex-sider-light'>
        <div>
          <div className='wks-name-ctr wks-name-ctr-light'>
            <Tooltip title={context.currentUser.profile.workspaceName} placement='right'>
              <div className='wks-name wks-name-light'>
                <Hexagon size={state.collapsed ? 23 : 20} />
                {user && !state.collapsed && (
                  <div className='wks-header'>
                    <div className='header'>
                      {context.currentUser.profile && <span style={onCollaps}>{trimText(context.currentUser.profile.workspaceName)}</span>}
                    </div>
                  </div>
                )}
              </div>
            </Tooltip>
          </div>
        </div>
        <div>
          <div style={{ display: "flex", flexDirection: "column" }}>
            <ConsoleLogo />
            <Tag style={{ margin: "0 15px 10px 15px" }} color='geekblue'>
              {packageJson?.version}
            </Tag>
          </div>
        </div>
      </Sider>
      <Sider trigger={null} collapsed={false} className='workspace-side-nav hex-sider-light'>
        <Dropdown overlay={orgMenu} trigger={["click"]} overlayStyle={{ width: 200 }}>
          <div className={`logo`} style={{ height: 52, marginBottom: 20 }}>
            {user && (
              <span className='brand-name'>{context.currentUser.profile && `${trimText(context.currentUser.profile.workspaceName)}`}</span>
            )}
            <DownOutlined />
          </div>
        </Dropdown>
        <CustomScroll heightRelativeToParent='calc(100vh - 100px)'>
          <Menu theme='light' mode='inline' defaultSelectedKeys={[]} defaultOpenKeys={["0"]}>
            <Menu.Item
              key='11'
              className='center-name add-datasource-btn'
              onClick={(e) =>
                history.push(
                  `/${context.currentUser.profile?.orgSlugId}/workspace/${context.currentUser.profile?.workspaceId}/add-datasource`
                )
              }
              icon={
                <i style={{ fontSize: 12, marginTop: 4, color: "#fff" }}>
                  <BsPlusLg />
                </i>
              }>
              {context.currentUser.profile && <span>Add Datasource</span>}
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
        <Dropdown overlay={orgMenu} trigger={["click"]} overlayStyle={{ width: 200 }}>
          <div className={`logo`} style={{ height: 52 }}>
            {context.currentUser.profile && (
              <OrgThumbnailImg name={context.currentUser.profile.orgName} thumbnail={context.currentUser.profile.orgThumbnail} />
            )}
            {user && (
              <span className='brand-name'>{context.currentUser.profile && `${trimText(context.currentUser.profile.orgName)}`}</span>
            )}
            <DownOutlined />
          </div>
        </Dropdown>
      </Sider>
      <Layout className='site-layout'>
        <Content
          className='site-layout-background'
          style={{
            minHeight: 280,
          }}>
          {content}
        </Content>
      </Layout>
    </Layout>
  );
};
export default connect(mapStateToProps, mapDispatchToProps)(WorkspaceMain);
