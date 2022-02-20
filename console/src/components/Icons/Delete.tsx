import React, { PureComponent } from "react";
import { Tag } from "antd";
import "./Icon.scss";

const Delete: React.FC<{}> = () => (
  <i className='anticon' style={{ marginRight: 8 }}>
    <svg width='0.96em' height='1em' viewBox='0 0 23.998 25.001'>
      <path
        data-name='Subtraction 17'
        d='M17.493 25.001H6.036a2.735 2.735 0 01-2.5-2.551L2.999 3.318h18l-.534 19.133c.001 1.383-1.825 2.55-2.972 2.55zm-1.249-18l-.491 13.991 2 .068.488-13.988-2-.071zm-8.49 0l-2 .071.491 13.988 2-.068-.488-13.991zm3.243.031v14h2v-14z'
        fill='#dfe2ef'
      />
      <path data-name='Union 16' d='M-.001 5V3h8V1a1 1 0 011-1h6a1 1 0 011 1v2h8v2z' fill='#b7bacf' />
    </svg>
  </i>
);
export default Delete;
