import React, { PureComponent } from "react";
import "./Icon.scss";

export default class ClusterIcon extends PureComponent<{ active?: boolean }> {
  render() {
    return (
      <i className='anticon'>
        <svg xmlns='http://www.w3.org/2000/svg' width='1.15em' height='1.1em' viewBox='0 0 25.031 22.327'>
          <g transform='translate(.001 .001)'>
            <path
              fill='#b7bacf'
              d='M9.919 18.475H2a2 2 0 01-2-2V2a2 2 0 012-2h3.456v13.347a2 2 0 002 2h4.464v1.128a2 2 0 01-2.001 2z'
              data-name='Subtraction 33'
              transform='rotate(180 12.515 9.237)'></path>
            <path
              fill='#b7bacf'
              d='M9.919 18.475H2a2 2 0 01-2-2V15.42h4.81a2 2 0 002-2V0h3.11a2 2 0 012 2v14.475a2 2 0 01-2.001 2z'
              data-name='Subtraction 34'
              transform='rotate(180 5.96 9.237)'></path>
            <g data-name='Group 574' transform='translate(6.553 4.619)'>
              <path
                fill='#a5aec0'
                opacity='0.6'
                d='M9.548 17.707H2a2 2 0 01-2-2V2a2 2 0 012-2h7.548a2 2 0 012 2v13.707a2 2 0 01-2 2zm-5.96-5.635a.893.893 0 100 1.786h4.373a.893.893 0 100-1.786zm0-3.08a.893.893 0 100 1.786h4.373a.893.893 0 100-1.786zM6 1.312a2.28 2.28 0 102.277 2.28A2.281 2.281 0 006 1.312z'
                data-name='Subtraction 35'
                transform='rotate(180 5.774 8.854)'></path>
            </g>
          </g>
        </svg>
      </i>
    );
  }
}
