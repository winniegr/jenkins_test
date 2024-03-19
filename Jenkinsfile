pipeline{
    agent {
        docker {
            image 'cimg/android:2023.09.1'
            args '-v $HOME/.m2:/home/.m2'
        }
    }
    stages {
        stage('Setup') {
          steps {
              sh 'chmod +x ./gradlew'
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
