package org.edgegallery.mecm.appo.bpmn.tasks;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.edgegallery.mecm.appo.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputParameters {

    private static final Logger LOGGER = LoggerFactory.getLogger(InputParameters.class);

    private final String action;
    private final DelegateExecution delegateExecution;

    public InputParameters(DelegateExecution delegateExecution) {
        this.action = (String) delegateExecution.getVariable("requestAction");
        this.delegateExecution = delegateExecution;
    }

    /**
     * Sets request inputs to the delegate execution.
     */
    public void setInputParameters() {
        switch (action) {
            case "CreateAppInstance":
                createAppInstance(delegateExecution);
                break;
            case "InstantiateAppInstance":
            case "TerminateAppInstance":
            case "QueryAppInstance":
                instantiateAppInstance(delegateExecution);
                break;
            case "QueryCapabilities":
            case "QueryKPI":
                queryCapabilities(delegateExecution);
                break;
            default:
                // Statements
        }
    }

    /**
     * Sets create app instance input parameters.
     *
     * @param delegateExecution delegate execution
     */
    void createAppInstance(DelegateExecution delegateExecution) {
        String accessToken = (String) delegateExecution.getVariable(Constants.ACCESS_TOKEN);
        String tenantId = (String) delegateExecution.getVariable(Constants.TENANT_ID);
        String appPkgId = (String) delegateExecution.getVariable(Constants.APP_PACKAGE_ID);
        String mecHost = (String) delegateExecution.getVariable(Constants.MEC_HOST);
        String appDescr = (String) delegateExecution.getVariable(Constants.APP_DESCR);
        String appId = (String) delegateExecution.getVariable(Constants.APP_ID);
        String appName = (String) delegateExecution.getVariable(Constants.APP_NAME);
        String appInstanceId = (String) delegateExecution.getVariable(Constants.APP_INSTANCE_ID);
        String hwCapabilities = (String) delegateExecution.getVariable(Constants.HW_CAPABILITIES);

        delegateExecution.setVariable(Constants.ACCESS_TOKEN, accessToken);
        delegateExecution.setVariable(Constants.TENANT_ID, tenantId);
        delegateExecution.setVariable(Constants.APP_PACKAGE_ID, appPkgId);
        delegateExecution.setVariable(Constants.MEC_HOST, mecHost);
        delegateExecution.setVariable(Constants.APP_DESCR, appDescr);
        delegateExecution.setVariable(Constants.APP_ID, appId);
        delegateExecution.setVariable(Constants.APP_NAME, appName);
        delegateExecution.setVariable(Constants.APP_INSTANCE_ID, appInstanceId);
        delegateExecution.setVariable(Constants.HW_CAPABILITIES, hwCapabilities);

        LOGGER.info("tenant_id: {},app_package_id: {},mec_host: {},app_instance_description: {},app_id: {},"
                        + "app_name: {},app_instance_id: {}, hw_capabilities: {}", tenantId, appPkgId, mecHost,
                appDescr, appId, appName, appInstanceId, hwCapabilities);
    }

    /**
     * Sets instantiate app instance input parameters.
     *
     * @param delegateExecution delegate execution
     */
    void instantiateAppInstance(DelegateExecution delegateExecution) {
        String accessToken = (String) delegateExecution.getVariable(Constants.ACCESS_TOKEN);
        String tenantId = (String) delegateExecution.getVariable(Constants.TENANT_ID);
        String appInstanceId = (String) delegateExecution.getVariable(Constants.APP_INSTANCE_ID);

        delegateExecution.setVariable(Constants.ACCESS_TOKEN, accessToken);
        delegateExecution.setVariable(Constants.TENANT_ID, tenantId);
        delegateExecution.setVariable(Constants.APP_INSTANCE_ID, appInstanceId);

        LOGGER.info("tenant_id: {}, app_instance_id: {}", tenantId, appInstanceId);
    }

    /**
     * Sets query capabilities request input parameters.
     *
     * @param delegateExecution delegate execution
     */
    void queryCapabilities(DelegateExecution delegateExecution) {
        String accessToken = (String) delegateExecution.getVariable(Constants.ACCESS_TOKEN);
        String tenantId = (String) delegateExecution.getVariable(Constants.TENANT_ID);
        String hostIp = (String) delegateExecution.getVariable(Constants.MEC_HOST);
        String capabilityId = (String) delegateExecution.getVariable(Constants.MEP_CAPABILITY_ID);
        delegateExecution.setVariable(Constants.ACCESS_TOKEN, accessToken);
        delegateExecution.setVariable(Constants.TENANT_ID, tenantId);
        delegateExecution.setVariable(Constants.MEC_HOST, hostIp);
        delegateExecution.setVariable(Constants.MEP_CAPABILITY_ID, capabilityId);

        LOGGER.info("tenant_id: {}, mec_host: {}", tenantId, hostIp);
    }
}
