# PhotoTrail Trip Album 편집 기능 구현 요약

## 1. 구현 목표
PhotoTrail 앱의 핵심 기능인 Trip Album(여행/추억 앨범)의 사용자 편집 기능을 완성하고, 자동 생성 로직과 사용자 편집값이 조화롭게 유지되도록 함.

## 2. 주요 기능 (구현 완료)

### A. Trip 병합 (Merge)
- Trip 목록에서 여러 개의 Trip을 선택하여 하나의 수동(Manual) Trip으로 병합.
- 병합된 Trip은 선택된 모든 날짜의 사진을 포함하며, 원본 Trip들은 숨김 처리됨.

### B. Trip 분리 (Split)
- 하나의 Trip을 날짜 단위로 선택하여 두 개의 새로운 Trip으로 분리.
- 기존 Trip은 보관(숨김) 처리하여 데이터 정합성 유지.

### C. 사진 수동 추가/제외 (Override)
- **추가 (INCLUDE):** 특정 Trip의 날짜 범위 밖의 사진도 수동으로 추가 가능.
- **제외 (EXCLUDE):** 자동 생성 규칙에 포함된 사진이라도 해당 Trip에서만 보이지 않게 처리.
- 실제 사진 파일은 수정하거나 이동하지 않으며, DB 내에서 매핑 정보만 관리.

### D. 대표 사진 직접 선택
- Trip 카드의 얼굴이 되는 대표 사진을 사용자가 직접 선택 가능.
- 자동 재생성 시에도 사용자가 선택한 대표 사진이 우선적으로 유지됨.

### E. 데이터 보존 및 동기화
- 사진 스캔 및 증분 동기화 후 Trip이 자동 재생성되어도 사용자의 편집(제목 수정, 숨김, 병합/분리 상태, 추가/제외 사진)이 유실되지 않음.

## 3. 기술적 변경 사항

### DB 스키마 변경 (Version 4 -> 5)
- **`trip_albums` 테이블 확장:** `isManual`, `sourceTripKeys`, `mergedIntoTripKey`, `customRepresentativePhotoUri` 등 7개 필드 추가.
- **`trip_photo_overrides` 테이블 신규 생성:** 사용자의 사진 추가/제외 기록을 저장하여 영속성 확보.

### 주요 수정 파일
- `PhotoRepository.kt`: 병합/분리/재정의 알고리즘 및 자동 생성 로직 고도화.
- `TripAlbumScreen.kt`: 병합 모드 UI, 분리 다이얼로그, 카드 메뉴 확장.
- `PhotoGridScreen.kt`: 사진 선택 모드 및 롱클릭 상황별 메뉴(대표 설정, 제외) 추가.
- `MainActivity.kt`: 편집 기능을 위한 복합 Navigation 경로 구성.

## 4. 향후 작업 추천 (TODO)
1. **사진 단위 세밀 분리:** 현재는 날짜 단위 분리만 가능하나, 동일 날짜 내에서도 시간/장소별 분리 기능 추가 필요.
2. **편집 취소 (Undo):** 병합이나 분리, 제외 처리를 복구할 수 있는 전용 관리 화면 구현.
3. **TripKey 안정화:** 사진 구성 변화에 더 강인한 Trip 식별 알고리즘 도입.

## 5. 빌드 결과
- **Status:** SUCCESS
- **Command:** `.\gradlew.bat assembleDebug` 확인 완료
