// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.revrobotics.CANSparkBase.ControlType;
import com.revrobotics.CANSparkLowLevel.MotorType;
import com.revrobotics.CANSparkMax;
import com.revrobotics.SparkPIDController;

import edu.wpi.first.units.Angle;
import edu.wpi.first.units.Distance;
import edu.wpi.first.units.Measure;
import edu.wpi.first.units.Units;
import edu.wpi.first.units.Velocity;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.Utilities;
import frc.robot.Constants.ShooterConstants;

public class Shooter extends SubsystemBase {
  /** Creates a new Shooter. */
  CANSparkMax leftKicker;
  CANSparkMax rightKicker;
  CANSparkMax leftFeederMotor;
  CANSparkMax rightFeederMotor;

  SparkPIDController leftController;
  SparkPIDController rightController;

  public Shooter() {
    leftKicker = new CANSparkMax(ShooterConstants.leftKickerMotorId, MotorType.kBrushless);
    rightKicker = new CANSparkMax(ShooterConstants.rightKickerMotorId, MotorType.kBrushless);
    leftFeederMotor = new CANSparkMax(ShooterConstants.leftFeederMotorID, MotorType.kBrushless);
    rightFeederMotor = new CANSparkMax(ShooterConstants.rightFeederMotorId, MotorType.kBrushless);

    rightFeederMotor.follow(leftFeederMotor, false);


    // setup Pid
    leftController = leftKicker.getPIDController();
    rightController = rightKicker.getPIDController();
    Utilities.setPID(leftController, ShooterConstants.leftPID);
    Utilities.setPID(rightController, ShooterConstants.rightPID);

    SmartDashboard.putData("Shooter/subsystem",this);

  }

  public void setLeftKickerMotorSpeed(Measure<Velocity<Angle>> velocity) {
    leftController.setReference(velocity.in(Units.RPM), ControlType.kVelocity);
  }

  public void setRightKickerMotorSpeed(Measure<Velocity<Angle>> velocity) {
  rightController.setReference(velocity.in(Units.RPM), ControlType.kVelocity);
  }

  public void stopRightKickerMotor() {
    setRightKickerMotorSpeed(Units.RPM.of(0));
  }

  public void stopLeftKickerMotor() {
    setLeftKickerMotorSpeed(Units.RPM.of(0));
  }

  public void stopKickerMotors() {
    stopLeftKickerMotor();
    stopRightKickerMotor();
  }

  private void setKickerSpeedsFromSmartDashboard() {
    setLeftKickerMotorSpeed(Units.RPM.of(SmartDashboard.getNumber("Shooter/leftSpeed", 0)));
    setRightKickerMotorSpeed(Units.RPM.of(SmartDashboard.getNumber("Shooter/rightSpeed", 0)));
  }

  public Command shootCommand() {
    return runEnd(this::setKickerSpeedsFromSmartDashboard, this::stopLeftKickerMotor);
  }
  /*
   * speed form 0 -1
   */
  public void setFeedMotorSpeed(double speed) {
    leftFeederMotor.set(speed);
  }
  public void stopFeedMotor() {
    setFeedMotorSpeed(0);
  }

  @Override
  public void periodic() {

    // This method will be called once per scheduler run
  }
}
