# PhotoTrail 프로젝트 요약

PhotoTrail은 기기 내 사진의 위치 정보(EXIF)를 분석하여 지도 위에 표시하고, 날짜별 또는 위치별로 사진을 그룹화하여 보여주는 Android 애플리케이션입니다.

## 🚀 주요 기능
1. **사진 인덱싱 (Scanning)**: 기기 저장소의 사진을 스캔하여 메타데이터(촬영일, 위치 정보)를 Room 데이터베이스에 저장합니다.
2. **지도 뷰 (Map View)**: Google Maps API를 사용하여 사진이 촬영된 위치를 클러스터링된 마커로 표시합니다.
3. **날짜별 그룹화**: 촬영 날짜별로 사진 리스트를 확인하고 관리할 수 있습니다.
4. **위치 기반 그룹화 (Bucketing)**: 인접한 위치에서 촬영된 사진들을 하나의 그룹으로 묶어 격자 형태로 제공합니다.
5. **위치 정보 없는 사진 관리**: GPS 데이터가 없는 사진들만 따로 모아서 볼 수 있는 기능을 제공합니다.

## 🛠 기술 스택
- **UI**: Jetpack Compose (Material 3)
- **Database**: Room Persistence Library
- **Image Loading**: Coil
- **Map**: Google Maps Compose Library
- **Metadata**: ExifInterface (androidx.exifinterface)
- **Architecture**: MVVM (ViewModel, StateFlow)

## 🔧 최근 수정 및 해결 사항

### 1. Icons.AutoMirrored 관련 크래시 해결
- **문제**: `java.lang.NoClassDefFoundError: Failed resolution of: Landroidx/compose/material/icons/Icons$AutoMirrored$Filled` 발생.
- **원인**: Compose Material Icons의 최신 기능인 `AutoMirrored` 클래스를 참조했으나, 해당 라이브러리(`material-icons-core`)가 명시적으로 의존성에 포함되지 않음.
- **해결**: `libs.versions.toml` 및 `app/build.gradle.kts`에 `androidx.compose.material:material-icons-core` 의존성을 추가하여 해결.

### 2. 사진 위치 정보 미표시 문제 해결
- **문제**: 사진 스캔 시 위치 정보(GPS)를 읽어오지 못하고 모두 '위치 정보 없음'으로 처리됨.
- **원인**: Android 10(API 29) 이상의 미디어 위치 정보 접근 정책(`ACCESS_MEDIA_LOCATION` 권한 필요) 누락.
- **해결**: 
    - `AndroidManifest.xml`에 `ACCESS_MEDIA_LOCATION` 권한 추가.
    - `HomeScreen.kt`의 권한 요청 로직에 해당 권한을 포함하여 런타임에 사용자 승인을 받도록 수정.

## 📂 주요 파일 구조
- `data/`: `PhotoRepository`, `PhotoDatabase`, `PhotoItemEntity` (데이터 처리 및 저장)
- `ui/`: 
    - `HomeScreen`: 메인 대시보드 및 스캔 진행 관리
    - `MapScreen`: Google Maps 기반 위치 표시
    - `PhotoListScreens`: 날짜별/위치별 사진 목록 표시
    - `PhotoViewModel`: UI 상태 및 비즈니스 로직 관리
