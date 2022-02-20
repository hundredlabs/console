import React, { PureComponent } from "react";
import "./Icon.scss";
import { statusColor, Color } from "./Colors";

export default class GridView extends PureComponent<{ color: Color }> {
  render() {
    const color = statusColor(this.props.color);

    return (
      <svg xmlns='http://www.w3.org/2000/svg' width='20' height='18' viewBox='0 0 20 18'>
        <defs>
          <clipPath id='clip-path'>
            <rect width='17' height='17' fill='none' />
          </clipPath>
        </defs>
        <g id='Repeat_Grid_7' data-name='Repeat Grid 7' clipPath='url(#clip-path)'>
          <g transform='translate(-617 -340)'>
            <rect id='Rectangle_37' data-name='Rectangle 37' width='8' height='8' transform='translate(617 340)' fill={color} />
          </g>
          <g transform='translate(-608 -340)'>
            <rect id='Rectangle_37-2' data-name='Rectangle 37' width='8' height='8' transform='translate(617 340)' fill={color} />
          </g>
          <g transform='translate(-617 -331)'>
            <rect id='Rectangle_37-3' data-name='Rectangle 37' width='8' height='8' transform='translate(617 340)' fill={color} />
          </g>
          <g transform='translate(-608 -331)'>
            <rect id='Rectangle_37-4' data-name='Rectangle 37' width='8' height='8' transform='translate(617 340)' fill={color} />
          </g>
        </g>
      </svg>
    );
  }
}
