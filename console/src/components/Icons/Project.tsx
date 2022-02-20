import React, { PureComponent } from "react";
import "./Icon.scss";
import { statusColor, Color } from "./Colors";

export default class Project extends PureComponent<{ active?: boolean }> {
  render() {
    return (
      <i className='anticon'>
        <svg xmlns='http://www.w3.org/2000/svg' width='1em' height='1em' viewBox='0 0 25 25'>
          <g fill='#a5aec0' transform='translate(-173 -133)'>
            <rect width='11' height='11' data-name='Rectangle 1629' opacity='0.8' rx='1' transform='translate(173 133)'></rect>
            <rect width='11' height='11' data-name='Rectangle 1629' rx='1' transform='translate(187 133)'></rect>
            <rect width='11' height='11' data-name='Rectangle 1629' rx='1' transform='translate(173 147)'></rect>
            <rect width='11' height='11' data-name='Rectangle 1629' opacity='0.8' rx='1' transform='translate(187 147)'></rect>
          </g>
        </svg>
      </i>
    );
  }
}
