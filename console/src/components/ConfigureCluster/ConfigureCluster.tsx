import React, { useState, FC } from "react";
import { LeftOutlined, RightOutlined } from "@ant-design/icons";
import { Input, Select, InputNumber, Button } from "antd";

import "./ConfigureCluster.scss";

import { IconAWS, IconGCP, IconAzure, CircleCheck } from "../Icons/PlatformIcons";
import { history } from "../../configureStore";
const { Option } = Select;
interface Provider {
  name: string;
  title: string;
  iconDefault: React.ReactNode;
  iconSelected: React.ReactNode;
}
interface ProviderProps {
  providers: Array<Provider>;
}

interface ClusterInstanceProps {
  id: string;
  name: string;
  selected: boolean;
  services: Array<string>;
}

const sampleClusters: Array<ClusterInstanceProps> = [
  {
    id: "j-KKNF1S17PT0Y",
    name: "dev-vistors-daily",
    selected: false,
    services: ["spark", "hive", "presto"],
  },
  {
    id: "j-KKRTS17PT0Y",
    name: "de-hits-landing",
    selected: false,
    services: ["spark", "hive", "presto"],
  },
  {
    id: "j-KKNF1SI3PT0Y",
    name: "lab-marketing",
    selected: false,
    services: ["spark", "hive", "presto"],
  },
  // {
  //   id: "j-KKNF1SI3PT0Y",
  //   name: "lab-daily-visits",
  //   selected: false,
  //   services: ["spark", "hive", "presto"],
  // },
  // {
  //   id: "j-KKNF1SI3PT0Y",
  //   name: "prod-clickstream",
  //   selected: false,
  //   services: ["spark", "hive", "presto"],
  // },
  // {
  //   id: "j-KKNF1SIERTT0Y",
  //   name: "lab-text-extraction",
  //   selected: false,
  //   services: ["spark", "hive", "presto"],
  // },
];

const providers: Array<Provider> = [
  {
    name: "AWS",
    title: "Amazon EMR",
    iconDefault: <IconAWS className='provider-icon' selected={false} />,
    iconSelected: <IconAWS className='provider-icon' selected={true} />,
  },
  {
    name: "GCP",
    title: "Google Dataproc",
    iconDefault: <IconGCP className='provider-icon' selected={false} />,
    iconSelected: <IconGCP className='provider-icon' selected={true} />,
  },
  {
    name: "Microsoft",
    title: "Azure HD Insight",
    iconDefault: <IconAzure className='provider-icon' selected={false} />,
    iconSelected: <IconAzure className='provider-icon' selected={true} />,
  },
];

const ClusterInstance: FC<ClusterInstanceProps> = (props) => {
  const [instance, setInstance] = useState<{ selected: boolean }>({
    selected: false,
  });
  return (
    <div
      className={`cluster-instance ${instance.selected ? "selected-instance card-shadow" : ""}`}
      style={{ width: 300 }}
      onClick={(e) => {
        setInstance({ selected: !instance.selected });
      }}>
      <div className='cluster-title'>
        <h5>{props.name}</h5>
        <div className='cluster-id'>{props.id}</div>
      </div>
    </div>
  );
};

const ClusterProvider: React.FC<ProviderProps> = (props) => {
  const [cluster, setProvider] = React.useState<{ providerName: string }>({
    providerName: "",
  });

  return (
    <>
      <h3>Choose a platform</h3>
      <div className='option-container'>
        {props.providers.map((p) => {
          const isSelected = p.name === cluster.providerName;
          return (
            <div
              key={p.name}
              className={isSelected ? "selected-option" : "cloud-option"}
              onClick={(e) => {
                setProvider({
                  providerName: p.name,
                });
              }}>
              <CircleCheck checked={isSelected} position='right' />
              {isSelected ? p.iconSelected : p.iconDefault}
              <h5>{p.title}</h5>
            </div>
          );
        })}
      </div>
    </>
  );
};

const ChooseOrBuildCluster: React.FC<{ clusters: Array<ClusterInstanceProps> }> = (props) => {
  return (
    <>
      <h3>Select Running Clusters</h3>
      <div className='cluster-set'>
        {props.clusters.map((c) => (
          <ClusterInstance id={c.id} name={c.name} services={c.services} selected={c.selected} />
        ))}
      </div>
    </>
  );
};

const ConfigureAlerts: React.FC<{}> = (props) => {
  const [alertForm, setAlertForm] = useState<{ rules: Set<number> }>({
    rules: new Set(),
  });

  const handleClick = (id: number) => {
    if (alertForm.rules.has(id)) {
      const postRemoval = alertForm.rules;
      alertForm.rules.delete(id);
      setAlertForm({ rules: postRemoval });
    } else {
      setAlertForm({ rules: alertForm.rules.add(id) });
    }
  };

  return (
    <>
      <h3>Configure alert settings</h3>
      <div className='rule-set'>
        <div className={`alert-rule ${alertForm.rules.has(1) ? "selected-rule card-shadow" : ""}`} key={1}>
          <CircleCheck checked={alertForm.rules.has(1)} position='left' onClick={(e) => handleClick(1)} />
          <span className='rule-start'>When the cluster is idle for more than </span>
          <InputNumber size='small' min={1} max={100000} defaultValue={3} width={100} disabled={!alertForm.rules.has(1)} />
          <span> hrs</span>
        </div>
        <div className={`alert-rule ${alertForm.rules.has(2) ? "selected-rule card-shadow" : ""}`} key={2}>
          <CircleCheck checked={alertForm.rules.has(2)} position='left' onClick={(e) => handleClick(2)} />
          <span className='rule-start'>When the EMR step with name </span>
          <Input size='small' placeholder='enter name' width={150} disabled={!alertForm.rules.has(2)} />
          <span> changes it's state from running to </span>
          <Select defaultValue='0' style={{ width: 120 }} disabled={!alertForm.rules.has(2)} size='small'>
            <Option value='0'>succeeded</Option>
            <Option value='1'>failed</Option>
          </Select>
          <span> hrs</span>
        </div>
        <div className={`alert-rule ${alertForm.rules.has(3) ? "selected-rule card-shadow" : ""}`} key={3}>
          <CircleCheck checked={alertForm.rules.has(3)} position='left' onClick={(e) => handleClick(3)} />
          <span className='rule-start'>When the HDFS utilization rises above </span>
          <InputNumber size='small' min={1} max={100} defaultValue={80} disabled={!alertForm.rules.has(3)} /> <span>%</span>
        </div>
        <div className={`alert-rule ${alertForm.rules.has(4) ? "selected-rule card-shadow" : ""}`} key={4}>
          <CircleCheck checked={alertForm.rules.has(4)} position='left' onClick={(e) => handleClick(4)} />
          <span className='rule-start'>When the Cluster memory utilization rises above </span>
          <InputNumber size='small' min={1} max={100} defaultValue={90} disabled={!alertForm.rules.has(4)} /> <span>%</span>
        </div>
      </div>
    </>
  );
};

export const ClusterBuilder: FC<{}> = (props) => {
  const forms = [<ClusterProvider providers={providers} />, <ChooseOrBuildCluster clusters={sampleClusters} />, <ConfigureAlerts />];
  const [builder, setBuilder] = useState<{ formIndex: number }>({
    formIndex: 0,
  });

  const onNext = () => {
    if (builder.formIndex < forms.length - 1) {
      setBuilder({ formIndex: builder.formIndex + 1 });
    } else {
      history.push("/clusters");
    }
  };

  const onBack = () => {
    if (builder.formIndex > 0) {
      setBuilder({ formIndex: builder.formIndex - 1 });
    } else {
      history.push("/clusters");
    }
  };

  return (
    <div className='cluster-form'>
      <div className='form-section'>
        <div>{`Step ${builder.formIndex + 1}/${forms.length}`}</div>
        {forms[builder.formIndex]}
        <div className='footer-btns'>
          <Button type='default' className='btn-action btn-prev' onClick={(e) => onBack()}>
            <LeftOutlined />
            Back
          </Button>
          <Button type='primary' className='btn-action btn-next' onClick={(e) => onNext()}>
            {builder.formIndex + 1 == forms.length ? "Configure" : "Next"}
            <RightOutlined style={{ right: 10 }} />
          </Button>
        </div>
      </div>
    </div>
  );
};
