import React, { PureComponent } from "react";
import "./Icon.scss";
import { statusColor, Color } from "./Colors";

type Props = {};
export default class ThreeDots extends PureComponent<{ color: Color; border?: boolean }> {
  render() {
    const color = statusColor(this.props.color);
    let cls = "";
    if (this.props.border) {
      cls = `icon-status-${this.props.color}`;
    }
    return (
      <div className={`icon-status ${cls}`}>
        <svg
          xmlns='http://www.w3.org/2000/svg'
          width='1em'
          height='1em'
          x='0'
          y='0'
          enableBackground='new 0 0 408 408'
          version='1.1'
          viewBox='0 0 408 408'
          xmlSpace='preserve'>
          <path
            fill={color}
            d='M51 153c-28.05 0-51 22.95-51 51s22.95 51 51 51 51-22.95 51-51-22.95-51-51-51zm306 0c-28.05 0-51 22.95-51 51s22.95 51 51 51 51-22.95 51-51-22.95-51-51-51zm-153 0c-28.05 0-51 22.95-51 51s22.95 51 51 51 51-22.95 51-51-22.95-51-51-51z'></path>
        </svg>
      </div>
    );
  }
}
