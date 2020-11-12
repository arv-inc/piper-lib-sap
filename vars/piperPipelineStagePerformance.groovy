import com.sap.piper.ConfigurationHelper
import com.sap.piper.GenerateStageDocumentation
import com.sap.piper.StageNameProvider
import com.sap.piper.Utils
import groovy.transform.Field

import static com.sap.piper.Prerequisites.checkScript

@Field String STEP_NAME = getClass().getName()
@Field String TECHNICAL_STAGE_NAME = 'performanceTests'

@Field Set GENERAL_CONFIG_KEYS = []
@Field STAGE_STEP_KEYS = [
    /** Can perform both deployments to cloud foundry and neo targets. Preferred over cloudFoundryDeploy and neoDeploy, if configured. */
    'multicloudDeploy',
    /** For Cloud Foundry use-cases: Creates CF Services based on information in yml-format.*/
    'cloudFoundryCreateService',
    /** For Cloud Foundry use-cases: Performs deployment to Cloud Foundry space/org. */
    'cloudFoundryDeploy',
    /** For Neo use-cases: Performs deployment to Neo landscape. */
    'neoDeploy',
    /** Executes Gatling performance tests */
    'gatlingExecuteTests',
    /**
     * Performs health check in order to prove one aspect of operational readiness.
     * In order to be able to respond to health checks from infrastructure components (like load balancers) it is important to provide one unprotected application endpoint which allows a judgement about the health of your application.
     */
    'healthExecuteCheck',
]
@Field Set STEP_CONFIG_KEYS = GENERAL_CONFIG_KEYS.plus(STAGE_STEP_KEYS)
@Field Set PARAMETER_KEYS = STEP_CONFIG_KEYS

/**
 * In this stage important performance-relevant checks will be conducted.<br />
 *
 * The stage will execute a Gatling test, if the step `gatlingExecuteTests` is configured.
 */
@GenerateStageDocumentation(defaultStageName = 'Performance')
void call(Map parameters = [:]) {

    def script = checkScript(this, parameters) ?: this
    def utils = parameters.juStabUtils ?: new Utils()
    def stageName = StageNameProvider.instance.getStageName(script, parameters, this)

    Map config = ConfigurationHelper.newInstance(this)
        .loadStepDefaults()
        .mixinGeneralConfig(script.commonPipelineEnvironment, GENERAL_CONFIG_KEYS)
        .mixinStageConfig(script.commonPipelineEnvironment, stageName, STEP_CONFIG_KEYS)
        .mixin(parameters, PARAMETER_KEYS)
        .addIfEmpty('multicloudDeploy', script.commonPipelineEnvironment.configuration.runStep?.get(stageName)?.multicloudDeploy)
        .addIfEmpty('cloudFoundryDeploy', script.commonPipelineEnvironment.configuration.runStep?.get(stageName)?.cloudFoundryDeploy)
        .addIfEmpty('cloudFoundryCreateService', script.commonPipelineEnvironment.configuration.runStep?.get(stageName)?.cloudFoundryCreateService)
        .addIfEmpty('healthExecuteCheck', script.commonPipelineEnvironment.configuration.runStep?.get(stageName)?.healthExecuteCheck)
        .addIfEmpty('neoDeploy', script.commonPipelineEnvironment.configuration.runStep?.get(stageName)?.neoDeploy)
        .addIfEmpty('gatlingExecuteTests', script.commonPipelineEnvironment.configuration.runStep?.get(stageName)?.gatlingExecuteTests)
        .use()

    piperStageWrapper (script: script, stageName: stageName) {

        // telemetry reporting
        utils.pushToSWA([step: STEP_NAME], config)

        // Prefer the newer multicloudDeploy step if it is configured as it is more capable
        if (config.multicloudDeploy) {
            durationMeasure(script: script, measurementName: 'deploy_test_multicloud_duration') {
                multicloudDeploy(script: script, stage: stageName)
            }
        } else {
            if (config.cloudFoundryCreateService) {
                cloudFoundryCreateService script: script
            }

            if (config.cloudFoundryDeploy) {
                durationMeasure(script: script, measurementName: 'deploy_test_cf_duration') {
                    cloudFoundryDeploy script: script
                }
            }

            if (config.neoDeploy) {
                durationMeasure(script: script, measurementName: 'deploy_test_neo_duration') {
                    neoDeploy script: script
                }
            }
        }

        if (config.healthExecuteCheck) {
            healthExecuteCheck script: script
        }

        if (config.gatlingExecuteTests) {
            durationMeasure(script: script, measurementName: 'gatling_duration') {
                gatlingExecuteTests script: script
            }
        }
    }
}
