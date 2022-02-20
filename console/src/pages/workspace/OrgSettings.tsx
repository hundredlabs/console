import React, { useState, useEffect, useContext } from "react";
import { Table, Upload, PageHeader, Form, message, Input, Button } from "antd";
import Workspace, { OrgDetail } from "../../services/Workspace";
import ImgCrop from "antd-img-crop";
import { UploadChangeParam, UploadFile } from "antd/lib/upload/interface";
import { useForm } from "antd/lib/form/Form";
import { history } from "../../configureStore";
import WebService from "../../services/WebService";
import { UserContext } from "../../store/User";
import AuthService, { MemberInfo } from "../../services/AuthService";

const OrgSetting: React.FC<{}> = () => {
  const [orgState, setOrgState] = useState<{
    loading: boolean;
    fileUploading: boolean;
    orgName?: string;
    slugId?: string;
    errorMessage?: string;
    orgLogoFile?: UploadFile<any>;
  }>({
    loading: false,
    fileUploading: false,
  });

  const [form] = useForm();
  const web = new WebService();
  const context = useContext(UserContext);

  const onPreview = async (file: UploadFile) => {
    let src: string | ArrayBuffer | undefined = file.url;
    if (!src) {
      src = await new Promise<string | ArrayBuffer | undefined>((resolve) => {
        const reader = new FileReader();
        file.originFileObj && reader.readAsDataURL(file.originFileObj);
        reader.onload = () => reader.result && resolve(reader.result);
      });
    }
    const image = new Image();
    src && typeof src === "string" && (image.src = src);
    const imgWindow = window.open(image.src);
    if (imgWindow != null) {
      imgWindow.document.write(image.outerHTML);
    }
  };

  const onUpload = (e: UploadChangeParam<UploadFile<any>>) => {
    const { status } = e.file;
    setOrgState({ ...orgState, orgLogoFile: e.file, fileUploading: true });
    if (status !== "uploading" && status !== "removed") {
    }
    if (status === "done") {
      message.success(`${e.file.name} file uploaded successfully.`);
      form.setFieldsValue({ thumbnailImg: e.file.response.path });
      setOrgState({ ...orgState, orgLogoFile: e.file, fileUploading: false });
    } else if (status === "error") {
      message.error(`${e.file.name} file upload failed.`);
    } else if (status === "removed") {
      setOrgState({ ...orgState, orgLogoFile: undefined, fileUploading: false });
    }
  };
  useEffect(() => {
    Workspace.getOrgDetail((d) => {
      setOrgState({ ...orgState, orgName: d.name, slugId: d.slugId });
    });
  }, []);

  const constructOrgUrl = (str: string) => {
    return str
      .replace(/\s+/g, "-")
      .toLowerCase()
      .replace(/[&\/\\#, +()\^$~%.@'":*?<>{}]/g, "");
  };

  const onOrgName = (e: React.ChangeEvent<HTMLInputElement>) => {
    //let orgUrl = constructOrgUrl(e.target.value);
    setOrgState({ ...orgState, orgName: e.target.value });
  };

  const onUrlChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    let orgUrl = e.target.value;
    setOrgState({ ...orgState, slugId: orgUrl });
  };

  const onFinish = (values: any) => {
    const orgInfo: OrgDetail = {
      name: values["name"],
      slugId: orgState.slugId || "",
      thumbnailImg: values["thumbnailImg"] || context.currentUser.profile?.orgThumbnail,
    };
    Workspace.updateOrgDetail(orgInfo, (r) => {
      history.push(`/${r.slugId}/settings`);
      message.success("Updated the org details");
      AuthService.accountInfo().then((account) => {
        if (account.exist) {
          const member = account as MemberInfo;
          context.updateUser(member.id, member.name, member.email, true, member.profile);
        }
      });
    });
  };

  let fieldData = [
    {
      name: "slugId",
      value: orgState.slugId,
    },
    {
      name: "name",
      value: orgState.orgName,
    },
  ];
  return (
    <div className='workspace-wrapper'>
      <PageHeader title='Account' subTitle='Manage your account setting' className='wizard-form' />
      <div>
        <div className='form-container'>
          <Form
            layout={"vertical"}
            fields={fieldData}
            form={form}
            name='basic'
            requiredMark={false}
            onFinish={onFinish}
            initialValues={{ accountType: "personal" }}
            className='create-form wizard-form'>
            <Form.Item name='thumbnailImg' label='Choose a thumbnail image for the profile, of approx size 256x256.'>
              <ImgCrop rotate>
                <Upload
                  action={`${web.getEndpoint()}/web/v1/org/upload-logo`}
                  withCredentials={true}
                  name='picture'
                  listType='picture-card'
                  accept='image/png,image/jpg,image/jpeg'
                  fileList={orgState.orgLogoFile ? [orgState.orgLogoFile] : []}
                  onChange={onUpload}
                  style={{ marginBottom: 20 }}
                  onPreview={onPreview}>
                  {orgState.orgLogoFile ? null : "+ Upload"}
                </Upload>
              </ImgCrop>
            </Form.Item>

            <Form.Item name='name' label='Name' rules={[{ required: true, message: "Please enter Organisation Name" }]}>
              <Input placeholder='Organisation Name' onChange={onOrgName} />
            </Form.Item>

            <Form.Item>
              {orgState.errorMessage && <div className='error-msg'>{orgState.errorMessage}</div>}
              <Button type='primary' htmlType='submit' className='btn-action btn-action-light' loading={orgState.loading}>
                Update
              </Button>
            </Form.Item>
          </Form>
        </div>
      </div>
    </div>
  );
};

export default OrgSetting;
