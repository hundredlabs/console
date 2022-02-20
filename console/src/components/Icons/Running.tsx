import React from "react";
import "./Icon.scss";
import { Color } from "./Colors";

const Running: React.FC<{ color: Color; isSquared?: boolean }> = (props) => (
  <div
    style={
      props.isSquared ? { borderTopLeftRadius: 4, borderTopRightRadius: 4, borderBottomLeftRadius: 4, borderBottomRightRadius: 4 } : {}
    }
    className='icon-status icon-status-active'></div>
);
const some = (
  <svg stroke='#5f72f2' style={{ height: 25, width: 25 }}>
    <g transform='translate(1 1)' strokeWidth={2} fill='none' fillRule='evenodd'>
      <circle strokeOpacity={0.5} cx={11.5} cy={11.5} r={11.5} />
      <path d='M20.174 19.05C26.7 11.554 21.645 5.086 19.05 2.827'>
        <animateTransform
          attributeName='transform'
          type='rotate'
          from='0 11.5 11.5'
          to='360 11.5 11.5'
          dur='0.5s'
          repeatCount='indefinite'
        />
      </path>
    </g>
  </svg>
);
export default Running;
