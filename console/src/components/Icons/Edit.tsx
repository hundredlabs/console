import React, { PureComponent } from "react";
import "./Icon.scss";

const Edit: React.FC<{}> = () => (
  <i className='anticon' style={{ marginRight: 8 }}>
    <svg width='1em' height='1em' viewBox='0 0 25 25'>
      <path d='M0 19.792v5.21h5.208L20.567 9.641l-5.208-5.208z' fill='#dfe2ef' />
      <path
        data-name='ic_edit_24px'
        d='M24.594 5.613a1.383 1.383 0 000-1.958L21.344.402a1.383 1.383 0 00-1.958 0l-2.541 2.545 5.208 5.208 2.541-2.541z'
        fill='rgba(223,226,239,0.8)'
      />
      <path data-name='Rectangle 1690' fill='#dfe2ef' d='M9 23h12v2H9z' />
      <path data-name='Rectangle 1691' fill='#dfe2ef' d='M22 23h3v2h-3z' />
    </svg>
  </i>
);
export default Edit;
