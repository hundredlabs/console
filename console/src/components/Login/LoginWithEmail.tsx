import * as React from "react";
import { Dispatch } from "redux";
import { useLocation } from "react-router-dom";
import { Layout, Button, Checkbox, Skeleton } from "antd";
import BrandLogo from "../../static/img/logo.png";
import MailIcon from "../../static/icons/mail.svg";
import KeyIcon from "../../static/icons/key.svg";
import "./Login.scss";
import AuthService, { ConfirmEmailResponse, MemberInfo } from "../../services/AuthService";
import { CheckboxChangeEvent } from "antd/lib/checkbox";
import FieldError from "../Commons/FieldError";
import { User } from "../../store/User";
import { updateLogin } from "../../actions/auth";
import { AppState } from "../../reducers";
import { connect } from "react-redux";
import { history } from "../../configureStore";
import { UserContext } from "../../store/User";
const { Content } = Layout;

function useQuery() {
  return new URLSearchParams(useLocation().search);
}
export interface ILoginProps {
  authToken?: string;
  user?: User;
  updateLogin?: typeof updateLogin;
  search?: string;
}
export interface IValues {
  /* Key value pairs for all the field values with key being the field name */
  [key: string]: any;
}
export interface ILoginState {
  email: string;
  password: string;
  rememberMe: boolean;
  errors: IErrors;
  loading: boolean;
  pageLoading: boolean;
  confirmation: ConfirmEmailResponse;
}

export interface IErrors {
  [key: string]: string;
}

const mapStateToProps = (state: AppState) => ({
  user: state.auth,
});
const mapDispatchToProps = (dispatch: Dispatch) => ({
  updateLogin: (id: number, email: string, name: string) => dispatch(updateLogin(id, email, name)),
});

const LoginWithEmailComponent: React.FC<ILoginProps> = ({ user, authToken, updateLogin, search }) => {
  const context = React.useContext(UserContext);
  const [state, setState] = React.useState<ILoginState>({
    email: "",
    password: "",
    errors: {},
    rememberMe: true,
    loading: false,
    pageLoading: true,
    confirmation: {
      message: "Validating the token ...",
      success: typeof authToken === "undefined",
    },
  });

  React.useEffect(() => {
    console.log(context.currentUser);
    if (context.currentUser.loggedIn) {
      history.push("/");
    } else {
      AuthService.accountInfo().then((account) => {
        console.log(account);
        if (account.exist) {
          history.push("/");
        } else if (authToken) {
          AuthService.confirmEmail(authToken).then((r) => {
            setState({ ...state, pageLoading: false, confirmation: r });
          });
        } else {
          setState({ ...state, pageLoading: false });
        }
      });
    }
  }, [user]);

  const handleLogin = (email: string, password: string, rememberMe: boolean) => {
    AuthService.login(email, password, rememberMe).then((v) => {
      setState({ ...state, loading: false, errors: v.errors });
      if (v.statusCode === 200 && v.success) {
        updateLogin && updateLogin(1, email, v.userName);
        AuthService.accountInfo().then((account) => {
          if (account.exist) {
            const member = account as MemberInfo;
            context.updateUser(member.id, member.name, member.email, true, member.profile);
            history.push(`/`);
          }
        });
        const nextUrl = new URLSearchParams(search).get("then");
        if (authToken) {
          history.push("/get-started");
        } else if (nextUrl !== null) {
          history.push(nextUrl);
        } else {
          history.push("/");
        }
      }
    });
  };

  const handleCheckEvent = (event: CheckboxChangeEvent): void => {
    setState({
      ...state,
      rememberMe: event.target.checked,
    });
  };

  const validateForm = (): boolean => {
    // TODO - validate form
    return true;
  };

  const handleInputChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const target = event.target;
    const value = target.value;
    const name = target.name;
    switch (name) {
      case "email":
        setState({ ...state, email: value });
        break;
      case "password":
        setState({ ...state, password: value });
    }
  };

  const handleSubmit = async (e: React.MouseEvent<HTMLFormElement>): Promise<void> => {
    e.preventDefault();
    if (validateForm()) {
      setState({ ...state, loading: true });
      handleLogin(state.email, state.password, state.rememberMe);
    }
  };

  return (
    <div className='login-wrapper'>
      {authToken && <h4 style={{ marginTop: 20, textAlign: "center" }}>{state.confirmation.message}</h4>}
      <div className='login-container'>
        <Content className='login-form-div-center'>
          <Skeleton loading={state.pageLoading} paragraph={true}>
            {state.confirmation.success && (
              <>
                <div className='brandDiv'>
                  <img src={BrandLogo} alt='Gigahex' />
                </div>
                <h3 className='title-centerd' style={{ marginBottom: 20 }}>
                  Log in to Gigahex
                </h3>
                <form className='formContainer' onSubmit={handleSubmit} autoComplete='off' style={{ width: 400 }}>
                  <div className='inputWrapper'>
                    <span>
                      <img src={MailIcon} alt='mail' />
                    </span>
                    <input placeholder='email or username' value={state.email} name='email' onChange={handleInputChange} />
                    {state.errors && <FieldError text={state.errors.email} visible={true} />}
                  </div>
                  <div className='inputWrapper'>
                    <span>
                      <img src={KeyIcon} alt='password' />
                    </span>
                    <input type='password' placeholder='Your password' value={state.password} name='password' onChange={handleInputChange} />
                    {state.errors.password && <FieldError text={state.errors.password} visible={true} />}
                  </div>
                  <div className='remember-me'>
                    <Checkbox checked={state.rememberMe} onChange={handleCheckEvent} name='rememberMe'>
                      Remember me
                    </Checkbox>
                  </div>
                  <div className='btnWrapper'>
                    <Button type='primary' htmlType='submit' onSubmit={handleSubmit} loading={state.loading} className='login-form-button'>
                      Login
                    </Button>
                  </div>
                </form>
              </>
            )}
          </Skeleton>
        </Content>
      </div>
    </div>
  );
};

export default connect(mapStateToProps, mapDispatchToProps)(LoginWithEmailComponent);
