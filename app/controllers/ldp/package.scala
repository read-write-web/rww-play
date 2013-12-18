package controllers.ldp


/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */

// We use plantain as current RWW Controller implementation
import controllers.plantain._

object ReadWriteWebController extends ReadWriteWebControllerPlantain(
  controllers.plantain.rwwRoot,
  controllers.plantain.rootContainerPath,
  controllers.plantain.rww
)

