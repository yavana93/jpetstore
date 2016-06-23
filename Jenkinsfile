node {
   // Mark the code checkout 'stage'....
   stage 'Checkout'

   // Checkout code from repository
   checkout scm

   // Get the maven tool.
   // ** NOTE: This 'M3' maven tool must be configured
   // **       in the global configuration.
   //def mvnHome = tool 'M3'
   def mvnHome= "C:\\Program Files\\Apache\\maven"
   env.JAVA_HOME = tool 'Java 1.8.0_73'

   // Mark the code build 'stage'....
   stage 'Build'
   // Run the maven build
   bat "${mvnHome}\\bin\\mvn clean"

}
