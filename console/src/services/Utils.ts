export const setLocalStorage = (name: string, data: any) => localStorage.setItem(name, JSON.stringify(data));
export const getLocalStorage = (name: string) => JSON.parse(localStorage.getItem(name));

const os = require("os");

export type TimeUnit = "sec" | "mins" | "hrs" | "day";
export const parseMilliseconds = (milliseconds: number) => {
  const roundTowardsZero = milliseconds > 0 ? Math.floor : Math.ceil;
  return {
    days: roundTowardsZero(milliseconds / 86400000),
    hours: roundTowardsZero(milliseconds / 3600000) % 24,
    minutes: roundTowardsZero(milliseconds / 60000) % 60,
    seconds: roundTowardsZero(milliseconds / 1000) % 60,
    milliseconds: roundTowardsZero(milliseconds) % 1000,
  };
};

export const calculatePer = (value: Number, totalValue: Number) => {
  return Number(((Number(value) / Number(totalValue)) * 100).toFixed(0));
};

export const bytesToSize = (bytes: number) => {
  const sizes = ["Bytes", "KB", "MB", "GB", "TB"];
  if (bytes === 0) return "0 B";
  const i = parseInt(Math.floor(Math.log(bytes) / Math.log(1024)).toString(), 10);
  if (i === 0) return `${bytes} ${sizes[i]}`;
  return `${(bytes / 1024 ** i).toFixed(1)} ${sizes[i]}`;
};

export const bytesToDecimalSize = (bytes: number) => {
  const sizes = ["Bytes", "KB", "MB", "GB", "TB"];
  if (bytes === 0) return "0 B";
  const i = parseInt(Math.floor(Math.log(bytes) / Math.log(1024)).toString(), 10);
  if (i === 0) return `${bytes} ${sizes[i]}`;
  return `${(bytes / 1000 ** i).toFixed(1)} ${sizes[i]}`;
};

export const getPercentage = (frac: number) => {
  return `${(frac * 100).toFixed(0)}%`;
};

export const getReadableTime = (milliseconds: number): string => {
  if (milliseconds / 86400000 > 1) {
    return `${parseMsInUnit(milliseconds, "day")} days`;
  } else if (milliseconds / 3600000 > 1) {
    return `${parseMsInUnit(milliseconds, "hrs")} hrs`;
  } else if (milliseconds / 60000 > 1) {
    return `${parseMsInUnit(milliseconds, "mins")} mins`;
  } else if (milliseconds / 1000 > 1) {
    return `${parseMsInUnit(milliseconds, "sec")} secs`;
  } else {
    return `${milliseconds} ms`;
  }
};

export const getTimeUnit = (milliseconds: number): TimeUnit => {
  if (milliseconds / 86400000 > 1) {
    return "day";
  } else if (milliseconds / 3600000 > 1) {
    return "hrs";
  } else if (milliseconds / 60000 > 1) {
    return "mins";
  } else {
    return "sec";
  }
};

export const parseMsInUnit = (milliseconds: number, unit: TimeUnit): number => {
  switch (unit) {
    case "day":
      return Number((milliseconds / 86400000).toFixed(1));
    case "hrs":
      return Number((milliseconds / 3600000).toFixed(1));
    case "mins":
      return Number((milliseconds / 60000).toFixed(1));
    case "sec":
      return Number((milliseconds / 1000).toFixed(1));
  }
};

// create Addon Yaml file
