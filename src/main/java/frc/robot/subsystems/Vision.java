// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.photonvision.EstimatedRobotPose;
import org.photonvision.PhotonCamera;
import org.photonvision.PhotonPoseEstimator;
import org.photonvision.PhotonPoseEstimator.PoseStrategy;
import edu.wpi.first.apriltag.AprilTagFieldLayout;
import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StructArrayPublisher;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;
import frc.robot.Constants.VisionConstants;

public class Vision extends SubsystemBase {
  private PhotonCamera frontCamera;
  private PhotonCamera backCamera;

  private PhotonPoseEstimator frontEstimator;
  private PhotonPoseEstimator backEstimator;

  private AprilTagFieldLayout fieldLayout;
  private double poseTimestamp;
  private Pose2d visionPose = new Pose2d(0.0, 0.0, new Rotation2d(0.0));
  private Field2d field2d;
  private Pose2d referencePose = new Pose2d(0.0, 0.0, new Rotation2d(0.0));
  private Consumer<VisionMeasurement> consumer;
  private Supplier<Pose2d> poseSupplier;
  public static class VisionMeasurement {
    public Pose2d pose;
    public double timeStamp;
    Matrix<N3, N1> dev;
    public VisionMeasurement (Pose2d pose,double timeStamp,Matrix<N3, N1> dev) {
      this.pose = pose;
      this.dev = dev;
      this.timeStamp = timeStamp;
    }
  }
  public Vision(Consumer<VisionMeasurement> consumer,Supplier<Pose2d> poseSupplier) {
    this.poseSupplier = poseSupplier;
    this.consumer = consumer;
    frontCamera = new PhotonCamera(VisionConstants.FRONT_CAMERA_NAME);
    backCamera = new PhotonCamera(VisionConstants.BACK_CAMERA_NAME);

    try {
      fieldLayout = AprilTagFieldLayout.loadFromResource(VisionConstants.FIELD_LAYOUT_RESOURCE_FILE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    frontEstimator = new PhotonPoseEstimator(fieldLayout, PoseStrategy.CLOSEST_TO_REFERENCE_POSE, frontCamera,
        VisionConstants.ROBOT_TO_FRONT_CAM);
    backEstimator = new PhotonPoseEstimator(fieldLayout, PoseStrategy.CLOSEST_TO_REFERENCE_POSE, backCamera,
        VisionConstants.ROBOT_TO_BACK_CAM);
    field2d = new Field2d();
    SmartDashboard.putData("Vision estimated Pose",field2d);
    
    poseTimestamp = Timer.getFPGATimestamp();
  }
  
  public Pose2d getVisionPose() {
    return visionPose;
  } 
  public Double getVisionTimestamp () {
    return poseTimestamp;
  }
  
  public Pose2d getReferencePose() {
    return referencePose;
  }
  public void setReferencePose(Pose2d referencePose) {
    this.referencePose = referencePose;
  } 
  StructArrayPublisher<Pose3d> frontTags = NetworkTableInstance.getDefault()
    .getStructArrayTopic("SmartDashboard/Vision/Front Tags", Pose3d.struct).publish();
  StructArrayPublisher<Pose3d> backTags = NetworkTableInstance.getDefault()
    .getStructArrayTopic("SmartDashboard/Vision/Back Tags", Pose3d.struct).publish();
  @Override
  public void periodic() {
    /* update estimated pose */
    referencePose = poseSupplier.get();
    frontEstimator.setReferencePose(referencePose);
    backEstimator.setReferencePose(referencePose);

    Optional<EstimatedRobotPose> frontEstimate = frontEstimator.update();
    Optional<EstimatedRobotPose> backEstimate  = backEstimator.update();

    if (frontEstimate.isPresent()) {
      consumer.accept(new VisionMeasurement(frontEstimate.get().estimatedPose.toPose2d(), frontEstimate.get().timestampSeconds, confidenceCalculator(frontEstimate.get())));
      //good old java
       frontTags.set(
          frontEstimate.get().targetsUsed.stream()
          .map((i)-> fieldLayout.getTagPose(i.getFiducialId()).get())
          .toArray(size -> new Pose3d[size])
          );
      
    } else {
      frontTags.set(new Pose3d[0]);
    }
    if (backEstimate.isPresent()) {
      consumer.accept(new VisionMeasurement(backEstimate.get().estimatedPose.toPose2d(), backEstimate.get().timestampSeconds, confidenceCalculator(backEstimate.get())));
      //good old java
       backTags.set(
          backEstimate.get().targetsUsed.stream()
          .map((i)-> fieldLayout.getTagPose(i.getFiducialId()).get())
          .toArray(size -> new Pose3d[size])
          );
      
    } else {
      backTags.set(new Pose3d[0]);
    }
    
    SmartDashboard.putBoolean("Vision/Front Camera Connected", frontCamera.isConnected());
    SmartDashboard.putBoolean("Vision/Back Camera Connected", backCamera.isConnected());
    if (!frontCamera.isConnected()) frontTags.set(new Pose3d[0]);
    if (!backCamera.isConnected()) backTags.set(new Pose3d[0]);
    field2d.setRobotPose(this.visionPose);
    
    SmartDashboard.putNumber("Vision/Estimated Angle",getVisionPose().getRotation().getDegrees());


  }
   private Matrix<N3, N1> confidenceCalculator(EstimatedRobotPose estimation) {
    double smallestDistance = Double.POSITIVE_INFINITY;
    for (var target : estimation.targetsUsed) {
      var t3d = target.getBestCameraToTarget();
      var distance = Math.sqrt(Math.pow(t3d.getX(), 2) + Math.pow(t3d.getY(), 2) + Math.pow(t3d.getZ(), 2));
      if (distance < smallestDistance)
        smallestDistance = distance;
    }
    double poseAmbiguityFactor = estimation.targetsUsed.size() != 1
        ? 1
        : Math.max(
            1,
            (estimation.targetsUsed.get(0).getPoseAmbiguity()
                + Constants.VisionConstants.POSE_AMBIGUITY_SHIFTER)
                * Constants.VisionConstants.POSE_AMBIGUITY_MULTIPLIER);
    double confidenceMultiplier = Math.max(1, (
      Math.max(1,
        Math.max(0, smallestDistance - Constants.VisionConstants.NOISY_DISTANCE_METERS)
        * Constants.VisionConstants.DISTANCE_WEIGHT
      ) * poseAmbiguityFactor)
    / (1 + ((estimation.targetsUsed.size() - 1) * Constants.VisionConstants.TAG_PRESENCE_WEIGHT)));

    return Constants.VisionConstants.VISION_MEASUREMENT_STANDARD_DEVIATIONS.times(confidenceMultiplier);
  }
}