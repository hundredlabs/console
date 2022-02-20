import React, { PureComponent } from "react";
import "./Icon.scss";
import { statusColor, Color } from "./Colors";

export default class CommandIcon extends PureComponent<{ color: Color }> {
  render() {
    const color = statusColor(this.props.color);
    return (
      <div className={`icon-status icon-status-${this.props.color}`}>
        <svg xmlns='http://www.w3.org/2000/svg' width='14.511' height='10.656' viewBox='0 0 14.511 10.656'>
          <g id='icon-command' transform='translate(-1030 -1360.667)'>
            <line
              id='Line_14'
              data-name='Line 14'
              x2='7.6'
              y2='0.078'
              transform='translate(1036.9 1370.089)'
              fill='none'
              stroke={color}
              strokeWidth='2'
            />
            <path
              id='ic_chevron_right_24px'
              d='M9.842,6,8.59,7.252l4.067,4.076L8.59,15.4l1.252,1.252,5.328-5.328Z'
              transform='translate(1021.41 1354.667)'
              fill={color}
            />
          </g>
        </svg>
      </div>
    );
  }
}
