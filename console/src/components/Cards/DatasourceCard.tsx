import { Button, Card, Space } from "antd";
import { FC } from "react";

interface CardProps {
  btnText: string;
  sourceName: string;
  sourceIcon: any;
  sourceDesc: string;
  onClickAdd: (name: string) => void;
}

const DatasourceCard: FC<CardProps> = ({ btnText, sourceDesc, sourceIcon, sourceName, onClickAdd }) => {
  return (
    <Card className='datasource-card'>
      <Space align='center' size='small'>
        {sourceIcon}
        <div className='soruce-name'>{sourceName}</div>
      </Space>
      <div className='source-desc'>{sourceDesc}</div>
      <Button className='add-btn' onClick={() => onClickAdd(sourceName)}>
        {btnText}
      </Button>
    </Card>
  );
};

export default DatasourceCard;
