import React, { PureComponent } from "react";
import { Tag } from "antd";
import "./Icon.scss";

const DocsIcn: React.FC<{ style?: React.CSSProperties }> = (props) => (
  <i className='anticon' style={props.style}>
    <svg width='1em' height='1em' viewBox='0 0 25 25'>
      <g data-name='Ellipse 174' fill='none' stroke='rgba(223,226,239,0.8)' strokeWidth={5}>
        <circle cx={12.5} cy={12.5} r={12.5} stroke='none' />
        <circle cx={12.5} cy={12.5} r={10} />
      </g>
      <path
        data-name='Subtraction 14'
        d='M12.5 21.5a9.008 9.008 0 01-9-9 9.01 9.01 0 019-9 9.01 9.01 0 019 9 9.008 9.008 0 01-9 9zm-1.022-4.908v2.044h2.043v-2.044zM12.5 8.409a2.049 2.049 0 012.047 2.046c0 .9-.582 1.345-1.257 1.862a3.664 3.664 0 00-1.812 3.25h2.043a3.162 3.162 0 011.473-2.405 3.492 3.492 0 001.6-2.707 4.1 4.1 0 00-4.09-4.09 4.1 4.1 0 00-4.09 4.09h2.043A2.049 2.049 0 0112.5 8.408z'
        fill='#dfe2ef'
      />
    </svg>
  </i>
);
export default DocsIcn;
