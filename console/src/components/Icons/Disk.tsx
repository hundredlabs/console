import React, { PureComponent } from "react";
import { Tag } from "antd";
import "./Icon.scss";
import { statusColor, Color } from "./Colors";

const Disk: React.FC<{}> = () => (
  <svg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24'>
    <g transform='translate(-483 -220)'>
      <rect width='24' height='24' fill='#dfe2ef' data-name='Rectangle 1613' rx='3' transform='translate(483 220)'></rect>
      <g data-name='Group 571' transform='translate(296 -112)'>
        <g transform='translate(191.25 337.193)'>
          <path
            fill='#252e71'
            d='M193 357.461s1.564-11.278 2.639-12 8.5-.41 9.481 0 2.932 12 2.932 12z'
            data-name='Path 529'
            opacity='0.49'
            transform='translate(-193 -345.035)'></path>
          <path
            fill='#57609a'
            d='M13.324 5.028H1.76A1.762 1.762 0 010 3.268V1.76A1.761 1.761 0 011.76 0h11.564a1.762 1.762 0 011.76 1.76v1.508a1.762 1.762 0 01-1.76 1.76zM8.59 2.933a.209.209 0 100 .418h4.189a.209.209 0 100-.418z'
            data-name='Subtraction 7'
            transform='translate(0 9.333)'></path>
        </g>
      </g>
    </g>
  </svg>
);

export default Disk;
