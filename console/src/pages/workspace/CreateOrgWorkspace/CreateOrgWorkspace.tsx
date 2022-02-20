import React, { FC, useContext, useState } from "react";
import { Dispatch } from "redux";
import { updateLogin, deleteSession } from "../../../actions/auth";
import { appLoaded } from "../../../actions/app";
import { User, UserContext } from "../../../store/User";
import { AppState } from "../../../reducers";
import { connect } from "react-redux";
import { Button, Form, Input } from "antd";
import "./CreateOrgWorkspace.scss";
import { RadioChangeEvent } from "antd/lib/radio";
import WorkspaceService from "../../../services/Workspace";
import { history } from "../../../configureStore";
import AuthService, { MemberInfo, MemberProfile } from "../../../services/AuthService";

interface IState {
  personalAcc: boolean;
  orgUrl: string;
  loading: boolean;
  errorMessage?: string;
}

const mapDispatchToProps = (dispatch: Dispatch) => ({
  logout: () => dispatch(deleteSession()),
  updateLogin: (id: number, email: string, name: string, profile?: MemberProfile) => dispatch(updateLogin(id, email, name, profile)),
  appLoaded: () => dispatch(appLoaded()),
});

const mapStateToProps = (state: AppState) => ({
  isAppLoaded: state.app.isAppLoaded,
  user: state.auth,
});

const CreateOrgWorkspace: FC<{ isAppLoaded: boolean; updateLogin?: typeof updateLogin; user?: User }> = ({ isAppLoaded, updateLogin, user }) => {
  const [state, setState] = useState<IState>({
    personalAcc: true,
    loading: false,
    orgUrl: "",
  });
  const context = useContext(UserContext);

  React.useEffect(() => {
    if (!isAppLoaded) {
      AuthService.accountInfo().then((account) => {
        if (account.exist && updateLogin) {
          //update the login state
          const accDetail = account as MemberInfo;
          context.updateUser(accDetail.id, accDetail.name, accDetail.email, true, accDetail.profile);

          if (accDetail.hasProfile) {
            history.push("/");
          }
        }
      });
    }
  }, []);
  const onFinish = (values: any) => {
    const wsName = values["name"];

    const orgName = values["orgName"];
    const orgSlug = values["orgSlug"];
    setState({
      ...state,
      loading: true,
      errorMessage: undefined,
    });
    WorkspaceService.onboardMember(
      wsName,
      true,
      orgName,
      orgSlug,
      (r) => {
        AuthService.accountInfo().then((account) => {
          if (account.exist && updateLogin) {
            //update the login state
            const accDetail = account as MemberInfo;
            context.updateUser(accDetail.id, accDetail.name, accDetail.email, true, accDetail.profile);
            if (accDetail.hasProfile) {
              history.push(`/${r.orgSlugId}/workspace/${r.workspaceId}/clusters`);
            }
          }
        });
      },
      (err) => {
        setState({
          ...state,
          errorMessage: err,
        });
      }
    );
  };

  const constructOrgUrl = (str: string) => {
    return str
      .replace(/\s+/g, "-")
      .toLowerCase()
      .replace(/[&\/\\#, +()\^$~%.@'":*?<>{}]/g, "");
  };

  const accountTypes = [
    { label: "Personal", value: "personal" },
    { label: "Organisation", value: "org" },
  ];

  // const onOrgName = (e: React.ChangeEvent<HTMLInputElement>) => {
  //   let orgUrl = constructOrgUrl(e.target.value);
  //   setState({ ...state, orgUrl });
  // };

  const onUrlChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    let orgUrl = e.target.value;
    setState({ ...state, orgUrl });
  };

  const onAccountChange = (e: RadioChangeEvent) => {
    if (e.target.value === "personal") {
      setState({ ...state, personalAcc: true });
    } else {
      setState({ ...state, personalAcc: false });
    }
  };

  let fieldData = [
    {
      name: "orgSlug",
      value: state.orgUrl,
    },
  ];

  return (
    <div className='create-org-workspace workspace-wrapper'>
      <div className='create-wrapper'>
        <div className='header'>
          {user && <h2 className='user-name'>Hey {context.currentUser.name}</h2>}
          <p className='title'>Create a Workspace</p>
          <p className='desc'>Workspace simplifies managing mulitple clusters and distributed jobs like Spark.</p>
        </div>
        <div className='form-container'>
          <Form layout={"vertical"} fields={fieldData} name='basic' onFinish={onFinish} requiredMark={false} initialValues={{ accountType: "personal" }} className='create-form wizard-form'>
            <Form.Item name='name' label='Workspace Name' rules={[{ required: true, message: "Please enter Workspace Name" }]}>
              <Input placeholder='Workspace Name' />
            </Form.Item>

            <Form.Item>
              {state.errorMessage && <div className='error-msg'>{state.errorMessage}</div>}
              <Button type='primary' htmlType='submit' className='btn-action btn-action-light' loading={state.loading}>
                Create Workspace
              </Button>
            </Form.Item>
          </Form>
        </div>
      </div>
    </div>
  );
};

export default connect(mapStateToProps, mapDispatchToProps)(CreateOrgWorkspace);
