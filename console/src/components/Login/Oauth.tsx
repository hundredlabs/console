import * as React from "react";
import { updateLogin } from "../../actions/auth";
import { AppState } from "../../reducers";
import { Dispatch } from "redux";
import { connect } from "react-redux";
import { history } from "../../configureStore";
import AuthService, { MemberInfo } from "../../services/AuthService";
import { UserContext } from "../../store/User";

const mapStateToProps = (state: AppState) => ({
  user: state.auth,
});
const mapDispatchToProps = (dispatch: Dispatch) => ({
  updateLogin: (id: number, email: string, name: string) => dispatch(updateLogin(id, email, name)),
});

const OAuth: React.FC<{ provider: string; location: string; updateLogin?: typeof updateLogin }> = ({ provider, location, updateLogin }) => {
  const context = React.useContext(UserContext);
  React.useEffect(() => {
    AuthService.updateAuthState(provider, location).then((v) => {
      if (v.statusCode === 200 && v.success) {
        AuthService.accountInfo().then((account) => {
          if (account.exist) {
            const member = account as MemberInfo;
            context.updateUser(member.id, member.name, member.email, true, member.profile);
            history.push(`/`);
          }
        });
      }
    });
  }, [location]);
  return <div></div>;
};

export default connect(mapStateToProps, mapDispatchToProps)(OAuth);
