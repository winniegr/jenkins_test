pipeline{
    agent {
        docker {
            image 'cimg/android:2023.09.1'
            // args  '-v $HOME/.gradle/:/root/.gradle/:rw'
           // args '-v $HOME/.gradle:/home/.gradle -v $HOME/.m2:/home/.m2'
        }
    }
     // environment {
     //    HOME = '~/'
     //    GRADLE_CACHE = '/tmp/gradle-user-home'
     //  }
    stages {
        // stage('Prepare container') {
        //   steps {
        //     // Copy the Gradle cache from the host, so we can write to it
        //     sh "rsync -a --include /caches --include /wrapper --exclude '/*' /home/.gradle $HOME/.gradle || true"
        //      sh "rsync -a --include /caches --include /wrapper --exclude '/*' /home/.m2 $HOME/.m2 || true"
        //   }
        // }
        stage('Setup') {
          steps {
              sh 'chmod +x ./gradlew'
              sh 'chmod 777 /home/.m2'
              sh 'chmod 777 /home/.gradle'
          }
        }
        stage('Build'){
             steps {
                sh './gradlew clean && rm -rf ./app/build/'
                sh './gradlew assembleRelease'
             }
        }
       stage('UnitTest'){
             steps {
                sh './gradlew test'
             }
        }
        stage('Archive') {  
            steps {
                archiveArtifacts artifacts: 'app/build/outputs/**/*.apk', fingerprint: true 
            }
        }
    }

}
