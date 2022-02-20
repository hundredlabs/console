import React, { PureComponent } from "react";
import "./Icon.scss";

const Notify: React.FC<{}> = () => (
  <i className='anticon' style={{ marginRight: 8 }}>
    <svg width='0.8em' height='1em' viewBox='0 0 19.595 25'>
      <path
        d='M9.8 25a2.516 2.516 0 002.449-2.564h-4.9A2.508 2.508 0 009.8 25zm7.348-7.692V10.9c0-3.936-2.008-7.231-5.511-8.1v-.877a1.839 1.839 0 10-3.674 0v.872c-3.515.872-5.511 4.154-5.511 8.1v6.41L0 19.872v1.282h19.595v-1.282z'
        fill='#dfe2ef'
      />
    </svg>
  </i>
);
export default Notify;
