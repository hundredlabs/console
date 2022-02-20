import * as React from "react";
import "./commons.scss";

interface IFieldMessage {
  text: string;
  visible: boolean;
}
const FieldError: React.FC<IFieldMessage> = props => {
  return <div className='error-container'>{props.visible && <span className='error'>{props.text}</span>}</div>;
};

export default FieldError;
