package com.yision.phantom.logistics.courier.flight;

public record AirCourierFlightProfile(

	int takeoffTicks,
	double takeoffSpeed,
	double takeoffForwardDistance,
	double takeoffAltitudeGain,
	double takeoffSwitchDistance,

	double cruiseSpeed,
	double cruiseTurnDegrees,
	double cruiseCurveNear,
	double cruiseCurveFar,
	int cruiseStraightenTicks,

	double landingSpeed,
	double landingTurnDegrees,
	double landingCurveNear,
	double landingCurveFar,

	double curveDistanceMin,
	double curveDistanceMax,

	double phantomPortCruiseHeight,
	double phantomPortLandingHeight,
	double phantomPortCompletionDistance,

	double playerTargetHeight,
	double playerForwardOffset,
	double playerCruiseHeight,
	double playerCompletionDistance,

	double approachGateNearDistance,
	double approachGateHorizontalThreshold,
	double phantomPortApproachHeight,
	double playerApproachHeight,

	double landingMinSpeed,
	double landingDecelerationRange,
	double landingMaxDownSpeed,
	double landingMaxUpSpeed
) {
	public static final AirCourierFlightProfile DEFAULT = new AirCourierFlightProfile(

		32, 0.28, 9.0, 4.7, 0.75,

		0.40, 6.0, 0.40, 0.06, 40,

		0.32, 12.0, 0.55, 0.18,

		5.0, 60.0,

		4.0, 0.55, 0.2,

		1.2, 0.15, 1.8, 1.5,

		2.0, 5.0, 3.5, 1.6,

		0.10, 8.0, 0.22, 0.14
	);
}
