export type Color = "green" | "blue" | "red" | "grey" | "white" | "yellow";

export const statusColor = (color: Color): string => {
  let colorCode = "#000";
  switch (color) {
    case "green":
      colorCode = "#2ce5c2";
      break;
    case "blue":
      colorCode = "#5f72f2";
      break;
    case "yellow":
      colorCode = "#ffbb02";
      break;
    case "red":
      colorCode = "#dc5959";
      break;
    case "grey":
      colorCode = "#a5aec0";
      break;
    case "white":
      colorCode = "#fff";
      break;
  }
  return colorCode;
};
