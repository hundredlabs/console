import React, { PureComponent } from "react";
import "./Icon.scss";
import { statusColor, Color } from "./Colors";

export default class ScriptIcon extends PureComponent<{ color: Color }> {
  render() {
    const color = statusColor(this.props.color);
    return (
      <div className={`icon-status icon-status-${this.props.color}`}>
        <svg xmlns='http://www.w3.org/2000/svg' width='14.611' height='17' viewBox='0 0 14.611 17'>
          <g id='icon-script-filled' transform='translate(-374.895 -185)'>
            <line
              id='Line_62'
              data-name='Line 62'
              x2='14.6'
              y2='0.078'
              transform='translate(374.9 200.422)'
              fill='none'
              stroke={color}
              strokeWidth='2'
            />
            <text id='_' data-name='#!' transform='translate(376 198)' fill={color} fontSize='14' fontFamily='Roboto-Regular, Roboto'>
              <tspan x='0' y='0'>
                #!
              </tspan>
            </text>
          </g>
        </svg>
      </div>
    );
  }
}
