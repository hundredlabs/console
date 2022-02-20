import React, { PureComponent } from "react";
import "./Icon.scss";
import { statusColor, Color } from "./Colors";

export default class Done extends PureComponent<{ color: Color; border?: boolean }> {
  render() {
    const color = statusColor(this.props.color);
    let cls = "";
    if (this.props.border) {
      cls = `icon-status-${this.props.color}`;
    }
    return (
      <div className={`icon-status ${cls}`}>
        <svg xmlns='http://www.w3.org/2000/svg' width='11.008' height='9.677' viewBox='0 0 11.008 9.677'>
          <path
            fill='#fff'
            stroke='#fff'
            strokeWidth='1'
            d='M3.969 7.303L1.523 4.857l-.816.816 3.262 3.262 6.329-7.627L9.53.694 7.152 3.521z'></path>
        </svg>
      </div>
    );
  }
}
