import * as React from "react";
import { Status } from "../../services/Workspace";
import Done from "../../components/Icons/DoneIcon";
import Running from "../../components/Icons/Running";
import ThreeDots from "../../components/Icons/ThreeDots";
import Cross from "../../components/Icons/Cross";

export const StatusIcon: React.FC<{ status: Status }> = (props) => {
  switch (props.status) {
    case "completed":
    case "passed":
    case "succeeded":
      return <Done color='green' border={true} />;
    case "running":
      return <Running color='blue' />;
    case "not started":
      return <ThreeDots color='white' border={true} />;
    case "waiting":
      return <ThreeDots color='white' border={true} />;
    case "failed":
      return <Cross color='red' border={true} />;
    default:
      return <ThreeDots color='grey' />;
  }
};
