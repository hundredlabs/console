import React, { FC, PureComponent } from "react";
import "./Icon.scss";

export const IcnTemplates: React.FC<{ style?: React.CSSProperties }> = (props) => (
  <i className='anticon' style={props.style}>
    <svg xmlns='http://www.w3.org/2000/svg' width='1.2em' height='1.2em' viewBox='0 0 30 30' fill='#dfe2ef'>
      <path d='M24.707 7.793l-5.5-5.5A1 1 0 0018.5 2H7a2 2 0 00-2 2v22a2 2 0 002 2h16a2 2 0 002-2V8.5a1 1 0 00-.293-.707zM17 23h-7a1 1 0 010-2h7a1 1 0 010 2zm3-4H10a1 1 0 010-2h10a1 1 0 010 2zm0-4H10a1 1 0 010-2h10a1 1 0 010 2zm-1-6a1 1 0 01-1-1V3.904L23.096 9H19z'></path>
    </svg>
  </i>
);

export const IcnPlus: React.FC<{}> = () => (
  <i className='anticon'>
    <svg xmlns='http://www.w3.org/2000/svg' width='18' height='18' viewBox='0 0 18 18' fill='#dfe2ef'>
      <path
        id='ic_add_box_24px'
        d='M19,3H5A2,2,0,0,0,3,5V19a2,2,0,0,0,2,2H19a2.006,2.006,0,0,0,2-2V5A2.006,2.006,0,0,0,19,3ZM17,13H13v4H11V13H7V11h4V7h2v4h4Z'
        transform='translate(-3 -3)'
      />
    </svg>
  </i>
);

export const Hexagon: FC<{ size: number }> = ({ size }) => {
  return (
    <i className='anticon' style={{ fontSize: size }}>
      <svg xmlns='http://www.w3.org/2000/svg' id='Capa_1' viewBox='0 0 490 490' width='1em' height='1em'>
        <g fill='#a5aec0'>
          <path d='M466.032,367.5v-245L245,0L23.968,122.5v245L245,490L466.032,367.5z M118.865,175.094L245,105.188l126.136,69.906v139.813 L245,384.813l-126.135-69.906V175.094z' />
          <polygon points='331.299,292.828 331.299,197.172 245,149.343 158.701,197.172 158.701,292.828 245,340.657' />
        </g>
      </svg>
    </i>
  );
};
const NavIcons = {};
