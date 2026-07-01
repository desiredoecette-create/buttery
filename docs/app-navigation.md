# App navigation

Navigation destinations are shared product concepts. Android chooses bottom bar, rail, or expanded
dashboard chrome from window size; iOS uses native SwiftUI navigation.

## Dashboard flow

Launch → session restore → dashboard. The dashboard surfaces continue-recipe, recent recipes,
favorites, albums, grocery list, inbox status, import/create actions, and profile. Expanded
Android combines these in a landscape dashboard; compact Android and iPhone present prioritized
cards and bottom-tab destinations.

## Browse recipes flow

Dashboard/Recipes → recipe library → search/filter → recipe detail → edit, favorite, add to album,
share, or start cooking. Expanded widths may retain library and detail panes. Compact widths push
detail as a single destination.

## Favorites flow

Dashboard/Favorites → filtered recipe library → recipe detail → cooking or sharing. Favorites are
a recipe property in contract v1, not duplicated recipe documents.

## Grocery flow

Dashboard/Grocery → typed or drawing mode → edit/save/clear → return. Compact layouts prioritize
one editing surface. Expanded layouts may show controls and canvas side by side.

## Sharing flow

Recipe detail → Share → recipient lookup → optional message → confirmation. Recipient:
Inbox → shared recipe preview → accept/import or dismiss. Status transitions must be idempotent.

## Authentication flow

Unauthenticated launch → welcome/login → email or federated sign-in → username completion when
required → dashboard. Profile → sign out returns to welcome. Deep links requiring authentication
retain their intended destination and resume only after a successful session.

## Continue recipe and cooking

Dashboard → Continue Recipe resumes persisted cooking state. Recipe detail → Start Cooking creates
that state. Cooking mode may enable keep-awake and immersive policies; leaving or finishing
restores normal system UI behavior.

## Ambient mode

On eligible tablet/mounted configurations, inactivity may enter ambient slideshow mode. User input
returns to the prior destination. Phone and iPhone should not inherit this policy by screen-size
assumption; it is a product capability configured per device mode.
