import React, { PureComponent } from "react";
import "./Icon.scss";

export default class CopyIcon extends PureComponent<{}> {
  render() {
    return (
      <i className='anticon'>
        <svg width='1em' height='1em' viewBox='0 0 22 24'>
          <g transform='translate(-26 -608)' fill='#a5aec0'>
            <rect data-name='Rectangle 1679' width={18} height={20} rx={2} transform='translate(26 608)' opacity={0.4} />
            <rect data-name='Rectangle 1680' width={18} height={20} rx={2} transform='translate(30 612)' />
          </g>
        </svg>
      </i>
    );
  }
}
