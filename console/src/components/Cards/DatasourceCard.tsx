import { Button, Card, Space } from "antd";
import { FC } from "react";
import S3Img from "../../static/img/s3.png";
import PostgresImg from "../../static/img/pg.png";
import MySQLImg from "../../static/img/mysql.png";
import MariaDBImg from "../../static/img/maria.png";
import KafkaImg from "../../static/img/kafka.png";

interface CardProps {
  name: string;
  description: string;
  onClickAdd: (name: string) => void;
}

const ConnectionIcon: FC<{ name: string }> = ({ name }) => {
  switch (name.toLowerCase()) {
    case "s3":
      return <img src={S3Img} />;
    case "postgres":
      return <img src={PostgresImg} />;

    case "mysql":
      return <img src={MySQLImg} />;

    case "mariadb":
      return <img src={MariaDBImg} />;

    case "kafka":
      return <img src={KafkaImg} />;

    default:
      return <img src={S3Img} />;
  }
};

export const ConnectionCard: FC<CardProps> = ({ name, description, onClickAdd }) => {
  return (
    <Card
      className='datasource-card'
      title={<ConnectionIcon name={name} />}
      size='small'
      extra={
        <Button size='small' type='default' onClick={(e) => onClickAdd(name)}>
          Connect
        </Button>
      }>
      <div className='source-name'>{name}</div>
      <div className='source-desc'>{description}</div>
    </Card>
  );
};
