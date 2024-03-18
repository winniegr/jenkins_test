pipeline{
    agent {
        docker {
            image 'cimg/android:2023.09.1'
        }
    }
    stages {
        stage('Build'){
             steps {
                sh './gradlew clean && rm -rf ./app/build/'
                sh './gradlew assembleRelease'
             }
        }

    }

}
