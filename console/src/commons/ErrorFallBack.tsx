import React, { FC } from "react";
import { Alert } from "antd";
import { FallbackProps } from "react-error-boundary";
import "./ErrorFallBack.scss";

const ErrorFallback: FC<FallbackProps> = ({ error }) => {
  return (
    <div className='error-fallback'>
      <Alert message='Error' description={error.message} type='error' showIcon />
    </div>
  );
};

export default ErrorFallback;
