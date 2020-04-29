package org.apache.spark.ml.bundle.ops.classification

import ml.combust.bundle.BundleContext
import ml.combust.bundle.dsl._
import ml.combust.bundle.op.{OpModel, OpNode}
import ml.combust.mleap.tensor.DenseTensor
import org.apache.spark.ml.NaiveBayesShims
import org.apache.spark.ml.bundle.{ParamSpec, SimpleParamSpec, SimpleSparkOp, SparkBundleContext}
import org.apache.spark.ml.classification.NaiveBayesModel
import org.apache.spark.ml.linalg.{Matrices, Vectors}
import org.apache.spark.ml.param.ParamMap

/**
  * Created by fshabbir on 12/25/16.
  */
class NaiveBayesClassifierOp extends SimpleSparkOp[NaiveBayesModel] {
  override val Model: OpModel[SparkBundleContext, NaiveBayesModel] = new OpModel[SparkBundleContext, NaiveBayesModel] {
    override val klazz: Class[NaiveBayesModel] = classOf[NaiveBayesModel]

    override def opName: String = Bundle.BuiltinOps.classification.naive_bayes

    override def store(model: Model, obj: NaiveBayesModel)
                      (implicit context: BundleContext[SparkBundleContext]): Model = {
      val thresholds = if(obj.isSet(obj.thresholds)) {
        Some(obj.getThresholds)
      } else None
      val m = model.withValue("num_features", Value.long(obj.numFeatures)).
        withValue("num_classes", Value.long(obj.numClasses)).
        withValue("pi", Value.vector(obj.pi.toArray)).
        withValue("theta", Value.tensor(DenseTensor(obj.theta.toArray, Seq(obj.theta.numRows, obj.theta.numCols)))).
        withValue("model_type", Value.string(obj.getModelType)).
        withValue("thresholds", thresholds.map(Value.doubleList(_)))
      val sigmaOpt = NaiveBayesShims.getNaiveBayesModelSigma(obj)
      if (sigmaOpt.isDefined) {
        val sigma = sigmaOpt.get
        m.withValue("sigma", Value.tensor(DenseTensor(sigma.toArray, Seq(sigma.numRows, sigma.numCols))))
      } else {
        m
      }
    }

    override def load(model: Model)
                     (implicit context: BundleContext[SparkBundleContext]): NaiveBayesModel = {
      val theta = model.value("theta").getTensor[Double]
      val sigmaOpt = model.getValue("sigma").map { v =>
        val tensor = v.getTensor[Double]
        Matrices.dense(tensor.dimensions.head, tensor.dimensions(1), tensor.toArray)
      }
      val nb = NaiveBayesShims.createNaiveBayesModel(
        uid = "",
        pi = Vectors.dense(model.value("pi").getTensor[Double].toArray),
        theta = Matrices.dense(theta.dimensions.head, theta.dimensions(1), theta.toArray),
        sigmaOpt = sigmaOpt)
      val modelType = model.value("model_type").getString
      model.getValue("thresholds").map(t => nb.setThresholds(t.getDoubleList.toArray))
      nb.set(nb.modelType, modelType)
    }

  }

  override def sparkLoad(uid: String, shape: NodeShape, model: NaiveBayesModel): NaiveBayesModel = {
    val r = NaiveBayesShims.createNaiveBayesModel(uid = uid, pi = model.pi, theta = model.theta,
      sigmaOpt = NaiveBayesShims.getNaiveBayesModelSigma(model)
    )
    if (model.isDefined(model.thresholds)) { r.setThresholds(model.getThresholds) }
    if (model.isDefined(model.modelType)) { r.set(r.modelType, model.getModelType)}
    r
  }

  override def sparkInputs(obj: NaiveBayesModel): Seq[ParamSpec] = {
    Seq("features" -> obj.featuresCol)
  }

  override def sparkOutputs(obj: NaiveBayesModel): Seq[SimpleParamSpec] = {
    Seq("raw_prediction" -> obj.rawPredictionCol,
      "probability" -> obj.probabilityCol,
      "prediction" -> obj.predictionCol)
  }
}
