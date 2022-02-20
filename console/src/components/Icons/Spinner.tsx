import React, { PureComponent } from "react";
import "./Icon.scss";

type Props = {};
export default class Spinner extends PureComponent<Props> {
  render() {
    return <div className='loading'></div>;
  }
}
