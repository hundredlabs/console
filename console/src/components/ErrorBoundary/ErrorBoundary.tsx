import * as React from "react";
import { Button, Row, Col } from "antd";
import { history } from "../../configureStore";
import "./ErrorBoundary.scss";
import BrandLogo from "../../static/img/logo.png";

type ErrorState = {
  hasError: boolean;
  error: Error;
};
const MISSING_ERROR = "Error was swallowed during propagation.";

export default class ErrorBoundary extends React.PureComponent<{}, ErrorState> {
  readonly state: ErrorState = {
    hasError: false,
    error: new Error(MISSING_ERROR),
  };

  componentDidCatch(error: Error, info: object) {
    this.setState({ error: error, hasError: true });
  }

  takeMeHome = () => {
    history.push("/");
  };

  render() {
    if (this.state.hasError) {
      return (
        <React.Fragment>
          <div className='centered-col'>
            <Row>
              <div className='brand-logo'>
                <a href='/' className=''>
                  <img src={BrandLogo} />
                </a>
              </div>
            </Row>
            <Row>
              <Col span={12}>
                <h1>Something went wrong</h1>
              </Col>
            </Row>
            <Row>
              <Col span={12}>
                <p>{this.state.error.message}</p>
                <div>
                  <Button type='primary' onClick={(e) => this.takeMeHome()}>
                    Take me Home
                  </Button>
                </div>
              </Col>
            </Row>
          </div>
        </React.Fragment>
      );
    } else return this.props.children;
  }
}
