/*
 *    Copyright 2020-2021 Huawei Technologies Co., Ltd.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */


package org.edgegallery.mecm.appo.bpmn.tasks;

import static org.edgegallery.mecm.appo.bpmn.tasks.Apm.FAILED_TO_LOAD_YAML;
import static org.edgegallery.mecm.appo.bpmn.tasks.Apm.FAILED_TO_UNZIP_CSAR;

import com.google.gson.Gson;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.edgegallery.mecm.appo.exception.AppoException;
import org.edgegallery.mecm.appo.model.AppInstanceDependency;
import org.edgegallery.mecm.appo.model.AppInstanceInfo;
import org.edgegallery.mecm.appo.model.AppRule;
import org.edgegallery.mecm.appo.model.AppServiceRequired;
import org.edgegallery.mecm.appo.service.AppInstanceInfoService;
import org.edgegallery.mecm.appo.utils.Constants;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * 部署前检查app包的依赖是否有对应实例.
 *
 * @author 21cn/cuijch
 * @date 2020/11/9
 */
public class DeComposeAppPkgTask extends ProcessflowAbstractTask {

    private static final String OPERATIONAL_STATUS_INSTANTIATED = "Instantiated";
    private static final String YAML_KEY_TOPOLOGY = "topology_template";
    private static final String YAML_KEY_NODES = "node_templates";
    private static final String YAML_KEY_APP_CONFIG = "app_configuration";
    private static final String YAML_KEY_PROPERTIES = "properties";
    private static final Logger LOGGER = LoggerFactory.getLogger(DeComposeAppPkgTask.class);
    private final DelegateExecution execution;
    private final String appPkgBasePath;
    private final AppInstanceInfoService appInstanceInfoService;

    /**
     * 构造函数.
     *
     * @param delegateExecution      执行对象
     * @param appPkgBasePath         包路径
     * @param appInstanceInfoService 应用实例信息
     */
    public DeComposeAppPkgTask(DelegateExecution delegateExecution, String appPkgBasePath,
                               AppInstanceInfoService appInstanceInfoService) {
        this.execution = delegateExecution;
        this.appPkgBasePath = appPkgBasePath;
        this.appInstanceInfoService = appInstanceInfoService;
    }

    /**
     * 执行体.
     */
    public void execute() {
        LOGGER.info("Decompose application package...");

        final String tenantId = (String) execution.getVariable(Constants.TENANT_ID);
        final String appInstanceId = (String) execution.getVariable(Constants.APP_INSTANCE_ID);
        final String appPackageId = (String) execution.getVariable(Constants.APP_PACKAGE_ID);
        final String appId = (String) execution.getVariable(Constants.APP_ID);
        final String mecHost = (String) execution.getVariable(Constants.MEC_HOST);

        LOGGER.info("param: tenant:{}, app_instance_id:{}, app_package_id:{}, app_id:{}, mec_host:{}", tenantId,
                appInstanceId, appPackageId, appId, mecHost);

        final String appPackagePath = appPkgBasePath + appInstanceId + Constants.SLASH + appPackageId
                + Constants.APP_PKG_EXT;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(appPackagePath))) {
            LOGGER.info("check application {} dependency", appId);
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().contains("/MainServiceTemplate.yaml")) {
                    Map<String, Object> mainTemplateMap = loadMainServiceTemplateYaml(zis);
                    updateApplicationDescriptor(mainTemplateMap);
                    break;
                }
            }

            setProcessflowResponseAttributes(execution, Constants.SUCCESS, Constants.PROCESS_FLOW_SUCCESS);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.error(FAILED_TO_UNZIP_CSAR);
            setProcessflowExceptionResponseAttributes(execution, e.getMessage(), Constants.PROCESS_FLOW_ERROR);
        } catch (AppoException e) {
            LOGGER.error(e.getMessage());
            setProcessflowExceptionResponseAttributes(execution, e.getMessage(), Constants.PROCESS_FLOW_ERROR);
        }
    }

    private Map<String, Object> loadMainServiceTemplateYaml(ZipInputStream zis) {
        Map<String, Object> mainTemplateMap;
        Yaml yaml = new Yaml(new SafeConstructor());
        try {
            mainTemplateMap = yaml.load(zis);
        } catch (YAMLException e) {
            LOGGER.error(FAILED_TO_LOAD_YAML);
            throw new AppoException(FAILED_TO_LOAD_YAML);
        }
        return mainTemplateMap;
    }

    /**
     * Updates application descriptor from main service template yaml to the exection environment.
     *
     * @param mainTemplateMap main template map
     */
    public void updateApplicationDescriptor(Map<String, Object> mainTemplateMap) {
        Map<String, Object> topology = (Map<String, Object>) mainTemplateMap.get(YAML_KEY_TOPOLOGY);
        if (topology == null) {
            LOGGER.error("topology template null in main service template yaml");
            return;
        }

        Map<String, Object> nodes = (Map<String, Object>) topology.get(YAML_KEY_NODES);
        if (nodes == null) {
            LOGGER.error("nodes null in main service template yaml");
            return;
        }
        Map<String, Object> appConfigs = (Map<String, Object>) nodes.get(YAML_KEY_APP_CONFIG);
        if (appConfigs == null) {
            LOGGER.error("appConfigs null in main service template yaml");
            return;
        }
        Map<String, Object> properties = (Map<String, Object>) appConfigs.get(YAML_KEY_PROPERTIES);
        if (properties == null) {
            LOGGER.error("properties null in main service template yaml");
            return;
        }

        ModelMapper mapper = new ModelMapper();
        AppRule appRule = mapper.map(properties, AppRule.class);

        checkMainTemplate(appRule);

        if (appRule.getAppTrafficRule() == null && appRule.getAppDNSRule() == null) {
            return;
        }

        //Get app name from the input not from the package app name
        String appName = (String) execution.getVariable(Constants.APP_NAME);
        appRule.setAppName(appName);

        Gson gson = new Gson();
        String appRulejson = gson.toJson(appRule);
        execution.setVariable(Constants.APP_RULES, appRulejson);

        LOGGER.info("Set app rules : {}", appRulejson);
    }

    /**
     * 从appRule中获取依赖列表，如果非空，检查依赖是否被部署.
     *
     * @param appRule 包含依赖列表
     */
    public void checkMainTemplate(AppRule appRule) {
        if (appRule.getAppServiceRequired() == null) {
            return;
        }
        String tenantId = (String) execution.getVariable(Constants.TENANT_ID);
        String appInstanceId = (String) execution.getVariable(Constants.APP_INSTANCE_ID);
        String mecHost = (String) execution.getVariable(Constants.MEC_HOST);

        // 根据mec host筛选实例列表，按照appPkgId转化为map，过滤掉状态非active的实例
        List<AppInstanceInfo> appInstanceInfoListInHost = appInstanceInfoService
                .getAppInstanceInfoByMecHost(tenantId, mecHost);

        LOGGER.debug("app instance in mec host: {}, number:{}", mecHost, appInstanceInfoListInHost.size());

        Map<String, AppInstanceInfo> appInstanceInfoMapWithPkg = appInstanceInfoListInHost.stream()
                .filter(appInstanceInfo -> OPERATIONAL_STATUS_INSTANTIATED
                        .equals(appInstanceInfo.getOperationalStatus()))
                .collect(Collectors.toMap(AppInstanceInfo::getAppId, appInstanceInfo -> appInstanceInfo));

        Gson gson = new Gson();
        List<AppInstanceDependency> dependencies = new ArrayList<>();

        // 解析MainServiceTemplate.yaml，确认依赖的APP是否被部署
        for (AppServiceRequired required : appRule.getAppServiceRequired()) {
            // 对于平台服务，不存在packageId，不需要检查
            if (null == required.getPackageId() || "".equals(required.getPackageId())) {
                continue;
            }
            AppInstanceInfo appInstanceInfo = appInstanceInfoMapWithPkg.get(required.getAppId());
            if (appInstanceInfo == null) {
                throw new AppoException("dependency app " + required.getSerName() + " not deployed");
            }

            AppInstanceDependency dependency = new AppInstanceDependency();
            dependency.setTenant(tenantId);
            dependency.setAppInstanceId(appInstanceId);
            dependency.setDependencyAppInstanceId(appInstanceInfo.getAppInstanceId());
            dependencies.add(dependency);
        }

        String appRequiredJson = gson.toJson(dependencies);
        execution.setVariable(Constants.APP_REQUIRED, appRequiredJson);
        LOGGER.info("Set app dependencies : {}", appRequiredJson);

        // 因为appServiceRequired已被被序列化并存储到Constants.APP_REQUIRED了
        // 而后面AppRule也会被序列化并存储到Constants.APP_RULES
        // 因此把appServiceRequired设置为null
        appRule.setAppServiceRequired(null);
    }
}
