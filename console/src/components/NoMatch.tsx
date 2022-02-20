import React from "react";

interface Props {
  location?: string;
}
const NoMatch: React.FC<Props> = (props) => {
  return <div>No Match</div>;
};

export default NoMatch;
